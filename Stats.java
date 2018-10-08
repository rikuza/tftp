

public class Stats {

	private int totalDataBlocks = 0;
	private int totalAcks = 0;
	private int totalBytes = 0;
	private long startTime = 0L;

	Stats() {
		startTime = System.currentTimeMillis();
	}

// any other methods

	void printReport() {
		// compute time spent sending data blocks
		int milliSeconds = (int) (System.currentTimeMillis() - startTime);
		float speed = (float) (totalBytes * 8.0 / milliSeconds / 1000); // M bps
		System.out.println("\nTransfer stats:");
		System.out.println("\nFile size:\t\t\t" + totalBytes);
		System.out.printf("End-to-end transfer time:\t%.3f s\n", (float) milliSeconds / 1000);
		System.out.printf("End-to-end transfer rate:\t%.3f M bps\n", speed);
		System.out.println("Number of data messages sent:\t\t\t" + totalDataBlocks);
		System.out.println("Number of Acks received:\t\t\t" + totalAcks);


	}
	
	void setTotalDataBlocks(int totalDataBlocks) {
		this.totalDataBlocks = totalDataBlocks;
	}
	
	void setTotalAcks(int totalAcks) {
		this.totalAcks = totalAcks;
	}
	
	void setTotalBytes(int totalBytes) {
		this.totalBytes = totalBytes;
	}
	
	int getTotalBytes() {
		return totalBytes;
	}
	int getTotalAcks() {
		return totalAcks;
	}int getTotalDataBlocks() {
		return totalDataBlocks;
	}
}
