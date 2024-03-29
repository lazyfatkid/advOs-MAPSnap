package Node;

import java.util.*;

import static java.lang.Math.max;

public final class ThisNodeInfo extends NodeInfo{
    private final List<NodeInfo> neighbors;
    private final int totalNumberOfNodes;
    private int minPerActive;
    private int maxPerActive;
    private int minSendDelay;
    private int snapshotDelay;
    private int maxNumber;
    private int[] VectorClock;


    ThisNodeInfo(
            int uid,
            int totalNumberOfNodes,
            String hostName,
            int port,
            int minPerActive,
            int maxPerActive,
            int minSendDelay,
            int snapshotDelay,
            int maxNumber, int[] VectorClock
            ) {
        super(uid, hostName, port);
        neighbors = new ArrayList<>();
        this.totalNumberOfNodes = totalNumberOfNodes;
        this.minPerActive=minPerActive;
        this.maxPerActive=maxPerActive;
        this.minSendDelay=minSendDelay;
        this.snapshotDelay=snapshotDelay;
        this.maxNumber=maxNumber;
        this.VectorClock = new int[totalNumberOfNodes];
    }
    public boolean addNeighbor(NodeInfo neighbor){
        return neighbors.add(neighbor);
    }

    public List<NodeInfo> getNeighbors() {
        return neighbors;
    }

    public int getTotalNumberOfNodes() {
        return totalNumberOfNodes;
    }

    public int getMinPerActive() {
        return minPerActive;
    }

    public void setMinPerActive(int minPerActive) {
        this.minPerActive = minPerActive;
    }

    public int getMaxPerActive() {
        return maxPerActive;
    }

    public void setMaxPerActive(int maxPerActive) {
        this.maxPerActive = maxPerActive;
    }

    public int getMinSendDelay() {
        return minSendDelay;
    }

    public void setMinSendDelay(int minSendDelay) {
        this.minSendDelay = minSendDelay;
    }

    public int getSnapshotDelay() {
        return snapshotDelay;
    }

    public void setSnapshotDelay(int snapshotDelay) {
        this.snapshotDelay = snapshotDelay;
    }

    public int getMaxNumber() {
        return maxNumber;
    }

    public void setMaxNumber(int maxNumber) {
        this.maxNumber = maxNumber;
    }

    public int[]  getVectorClock() {return VectorClock;}

    public void setVectorClock(int[] VectorClock) {this.VectorClock = VectorClock;}

    public void incrementVectorClock () {
        this.VectorClock[this.getUid()]++;
    }

    public void mergeVectorClock(int[] mapVectorClock) {
        for (int i = 0; i < this.getTotalNumberOfNodes(); i++)
        {
            this.VectorClock[i] = max(this.VectorClock[i], mapVectorClock[i]);
        }
        this.VectorClock[this.getUid()]++;
    }

}
