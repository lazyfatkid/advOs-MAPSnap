package Node;

import org.springframework.stereotype.Service;

@Service
public class RootService {
    public void leaderElection(LeaderElectionMessage message) throws InterruptedException {
        System.out.println("processing leader election message");
        //TODO implement
        Thread.sleep(1000); // simulated delay TODO: remove
    }
}
