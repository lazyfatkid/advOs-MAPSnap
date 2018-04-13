package Node;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

@Controller
@Slf4j
public class SynchGhsController {
    private final SynchGhsService synchGhsService;
    private final SimpMessagingTemplate template;
    private final ThisNodeInfo thisNodeInfo;
    private final GateLock sendingInitialMwoeSearchMessage;
    private final MwoeSearchRoundSynchronizer mwoeSearchRoundSynchronizer;
    private final Object mwoeSearchBarrier;

    private final Runnable mwoeLocalMinWork;

    @Autowired
    public SynchGhsController(
            SynchGhsService synchGhsService,
            SimpMessagingTemplate template,
            @Qualifier("Node/NodeConfigurator/thisNodeInfo")
            ThisNodeInfo thisNodeInfo,
            @Qualifier("Node/SynchGhsConfig/sendingInitialMwoeSearchMessage")
            GateLock sendingInitialMwoeSearchMessage,
            @Qualifier("Node/LeaderElectionConfig/mwoeSearchRoundSynchronizer")
            MwoeSearchRoundSynchronizer mwoeSearchRoundSynchronizer
            ){
        this.synchGhsService = synchGhsService;
        this.template = template;
        this.thisNodeInfo = thisNodeInfo;
        this.sendingInitialMwoeSearchMessage = sendingInitialMwoeSearchMessage;
        this.mwoeSearchRoundSynchronizer = mwoeSearchRoundSynchronizer;

        mwoeSearchBarrier = new Object();
        mwoeLocalMinWork = () -> {
            Queue<MwoeCandidateMessage> mwoeCandidateMessages = mwoeSearchRoundSynchronizer.getMessagesThisRound();
            List<Edge> candidateEdges = mwoeCandidateMessages.parallelStream()
                    .map(MwoeCandidateMessage::getMwoeCandidate)
                    .collect(Collectors.toList());
            System.out.println("inside work ");
            synchGhsService.calcLocalMin(candidateEdges);
        };
    }

    @MessageMapping("/mwoeSearch")
    public void mwoeSearch(MwoeSearchMessage message) {
        /*TODO buffer messages from other phases and only process them when we receive notification from leader that we
        are going into that phase*/
        if(synchGhsService.isFromComponentNode(message.getComponentId())) {
            sendingInitialMwoeSearchMessage.enter();
            //need to have barrier here to prevent race condition between reading and writing isSearched
            // note that it is also written when phase is transitioned, but we should have guarantee that all mwoeSearch
            // has been completed by then
            synchronized(mwoeSearchBarrier) {
                if (synchGhsService.isSearched()) {
                    if (log.isDebugEnabled()) {
                        log.debug("<---received another MwoeSearch message {}", message);
                    }
                    sendMwoeReject(message.getSourceUID());
                } else {
                    synchGhsService.mwoeIntraComponentSearch(message.getSourceUID(), message.getComponentId());
                }
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeSearch message {}", message);
            }
            synchGhsService.mwoeInterComponentSearch(message.getSourceUID(), message.getComponentId());
        }
    }


    @MessageMapping("/mwoeCandidate")
    public void mwoeCandidate(MwoeCandidateMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
                log.trace("<---received  MwoeCandidate message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeCandidate message {}", message);
            }
            synchronized (mwoeLocalMinWork) {
                mwoeSearchRoundSynchronizer.enqueueAndRunIfReady(message, mwoeLocalMinWork);
            }
        }
    }

    @MessageMapping("/mwoeReject")
    public void mwoeReject(MwoeRejectMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
                log.trace("<---received  MwoeReject message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received MwoeReject message {}", message);
            }
            synchronized (mwoeLocalMinWork) {
                mwoeSearchRoundSynchronizer.incrementProgressAndRunIfReady(message.getPhaseNumber(), mwoeLocalMinWork);
            }
        }
    }

    @MessageMapping("/initiateMerge")
    public void initiateMerge(InitiateMergeMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
               // log.trace("<---received  initiateMerge message in if {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received initiateMerge message in else {}", message);
            }
            //TODO implement receiving initiate merge message (relay or whatever)
            Edge selectedMwoeEdge = message.getMwoeEdge();
            if(message.getComponentId()==thisNodeInfo.getComponentId())
            {
                System.out.println("Inside initate Merge if");
                if(thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid() || thisNodeInfo.getUid() == selectedMwoeEdge.getSecondUid())
                {
                    int otherComponentNode = (thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid()) ?
                            selectedMwoeEdge.getSecondUid() : selectedMwoeEdge.getFirstUid();
                    List<NodeInfo> neighbors= thisNodeInfo.getNeighbors();
                    for(NodeInfo n: neighbors)
                    {
                       if(n.getUid() == otherComponentNode)
                           thisNodeInfo.getTreeEdges().add(selectedMwoeEdge);
                    }
                }
                List<Edge> treeEdgeListSync = thisNodeInfo.getTreeEdges();
                synchronized(treeEdgeListSync) {
                    for (Iterator<Edge> itr = treeEdgeListSync.iterator(); itr.hasNext(); ) {
                        Edge edge = itr.next();
                        int targetUID;
                        if (edge.firstUid != thisNodeInfo.getUid())
                            targetUID = edge.firstUid;
                        else
                            targetUID = edge.secondUid;

                        if (targetUID != message.getSourceUID())
                            sendInitiateMerge(targetUID, selectedMwoeEdge);
                    }
                }

            }
            else if(thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid() || thisNodeInfo.getUid() == selectedMwoeEdge.getSecondUid())
            {
                System.out.println("Inside else");
                System.out.println("Nodes are in diff compoenent: IntiateMerge MEssage Processing");
                int targetUID = (thisNodeInfo.getUid()==selectedMwoeEdge.getFirstUid()) ?
                        selectedMwoeEdge.getSecondUid() : selectedMwoeEdge.getFirstUid();
                System.out.println("Message.getTarget ininite Merge" + message.getTarget());
                if(thisNodeInfo.getUid() == message.getTarget())
                {
                    System.out.println("Intitae merge thisNodeInfo.getUid() == message.getTarget()");
                    if(!thisNodeInfo.getTreeEdges().contains(selectedMwoeEdge)) {
                        System.out.println("Tree Edge List size: " + thisNodeInfo.getTreeEdges().size());
                        thisNodeInfo.getTreeEdges().add(selectedMwoeEdge);
                    }

                    else
                    {
                        System.out.println("Tree Edge List size: "+ thisNodeInfo.getTreeEdges().size());
                        int newLeader = Math.max(selectedMwoeEdge.getFirstUid(),selectedMwoeEdge.getSecondUid());
                        System.out.println("New Leader is:" + newLeader);
                        //TODO broadast newLeader in the new merged component
                    }

                }
            }

        }

    }

    @MessageMapping("/newLeader")
    public void newLeader(NewLeaderMessage message) {
        if(thisNodeInfo.getUid() != message.getTarget()) {
            if (log.isTraceEnabled()) {
                log.trace("<---received  newLeader message {}", message);
            }
        } else {
            if (log.isDebugEnabled()) {
                log.debug("<---received newLeader message {}", message);
            }

            //update this nodes component id with new leaders UID

            thisNodeInfo.setComponentId(message.getNewLeaderUID());

            // then relay that message to all its tree edges
            List<Edge> treeEdgeListSync = thisNodeInfo.getTreeEdges();
            synchronized(treeEdgeListSync) {
                for (Iterator<Edge> itr = treeEdgeListSync.iterator(); itr.hasNext();) {
                    Edge edge = itr.next();
                    int targetUID = edge.getFirstUid() != thisNodeInfo.getUid() ? edge.getFirstUid() : edge.getSecondUid();

                    if (targetUID != message.getSourceUID())
                        sendNewLeader(targetUID);
                }
            }
        }
    }
    public void sendMwoeSearch() throws MessagingException {
        MwoeSearchMessage message = new MwoeSearchMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                thisNodeInfo.getComponentId()
                );
        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeSearch message: {}", message);
        }
        template.convertAndSend("/topic/mwoeSearch", message);
        log.trace("MwoeSearch message sent");
    }

    public void sendMwoeCandidate(int targetUid, Edge candidate) throws MessagingException {
        MwoeCandidateMessage message = new MwoeCandidateMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid,
                candidate
        );
        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeCandidate message: {}", message);
        }
        template.convertAndSend("/topic/mwoeCandidate", message);
        log.trace("MwoeCandidate message sent");
    }

    public void sendMwoeReject(int targetUid) throws MessagingException {
        MwoeRejectMessage message = new MwoeRejectMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending MwoeReject message: {}", message);
        }
        template.convertAndSend("/topic/mwoeReject", message);
        log.trace("MwoeReject message sent");
    }

    public void sendInitiateMerge(int targetUid, Edge selectedMwoeEdge) throws MessagingException {
        InitiateMergeMessage message = new InitiateMergeMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid,
                selectedMwoeEdge,
                thisNodeInfo.getComponentId()
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending InitiateMerge message: {}", message);
        }
        template.convertAndSend("/topic/initiateMerge", message);
        log.trace("InitiateMerge message sent");
    }

    public void sendNewLeader(int targetUid) throws MessagingException {
        NewLeaderMessage message = new NewLeaderMessage(
                thisNodeInfo.getUid(),
                synchGhsService.getPhaseNumber(),
                targetUid,
                thisNodeInfo.getComponentId()
        );

        if(log.isDebugEnabled()){
            log.debug("--->sending NewLeaderMessage message: {}", message);
        }
        template.convertAndSend("/topic/newLeader", message);
        log.trace("NewLeader message sent");
    }
}