
package tftp;
import java.io.*;
import java.net.*;

public class TFtp {
	
	//mode ("octet")
	private static final String WRITE_REQ_MODE = "octet";
	private static final int TIMEOUT = 5;
	private static final int MAX_TIMEOUT = 100;
	private static final int DEFAULT_TRIES = 5;
	private static int lastPort;
	private static int nTimeouts;
	
	
	/*
	 * calculates and returns the timeout to set
	 */
	private static int computeTimeout(int timeouts) {
		//in ms
		int timeout = 0;
		
		if (timeouts == 1)
			timeout = TIMEOUT * 100;	
		else if( timeouts == 2)
			timeout = TIMEOUT * 200;
		 else if (timeout < TIMEOUT) 
				timeout = TIMEOUT * 300;
		else {
			timeout= TIMEOUT * timeouts * 100;
			if (timeout > MAX_TIMEOUT)
				timeout = MAX_TIMEOUT;
		}

		return TIMEOUT*100;
	}

	/*
	 * checks if the tf packet is an ack and if it is for the expected block
	 * returns true if the packet is an ACK and the block is the expected block
	 */
	private static boolean checkACK(TFtpPacketV18 packet, int expectedBlock) {
		
		if(packet.getOpCode().equals(TFtpPacketV18.OpCode.OP_ERROR)) {
			System.out.println(packet.getErrorMessage());
			return false;
		}

		return (packet.getOpCode().equals(TFtpPacketV18.OpCode.OP_ACK) || packet.getOpCode().equals(TFtpPacketV18.OpCode.OP_OACK)) && packet.getBlockNumber() == expectedBlock;
	}


	/*
	* 
	*/
	private static void sendFile(Stats stats, String fileName, InetAddress server, int port) {
		
		try {
			//create new socket
			DatagramSocket socket = new DatagramSocket();
			//init global variables
			nTimeouts = 0;

			//control
			int blocksNeeded = 0;
			int seq = 0; 
			
			//read the file
			byte[] buffer = readFile(fileName);
			int len = buffer.length;
			
			//try to send write request packet
			DatagramPacket wrqPacket = prepareWriteRequest(socket, fileName, WRITE_REQ_MODE);
			wrqPacket.setAddress(server);
			wrqPacket.setPort(port);
			
			//sends datagram and waits for ack
			if (sendDatagramPacket(stats, socket, wrqPacket, seq)) {
				seq++;
				System.out.printf("packet %d sent\n", seq);
			}
			
			
			blocksNeeded = seq + len/TFtpPacketV18.TFTP_BLOCK_SIZE;
			
			//main loop
			while (seq < blocksNeeded) {
				
				DatagramPacket dataPacket = prepareDataPacket(stats, socket, seq, buffer, stats.getTotalBytes());
				dataPacket.setAddress(server);
				dataPacket.setPort(lastPort);
				
				if (sendDatagramPacket(stats, socket, dataPacket, seq)) {
					System.out.printf("packet %d sent\n bytes sent %d \n", seq, stats.getTotalBytes());
					seq++;
				}
						
			}
			
			//if the file has a multiple of 512 bytes, send an empty payload packet
			if (len%TFtpPacketV18.TFTP_BLOCK_SIZE == 0) {
				DatagramPacket lastPacket = prepareEmptyPacket(socket, seq);
				lastPacket.setAddress(server);
				lastPacket.setPort(lastPort);
				
				if (sendDatagramPacket(stats, socket, lastPacket, seq)) {
					seq++;
					System.out.printf("packet %d sent\n", seq);
				}
			} else  { //send the last packet
				
				DatagramPacket packet = prepareDataPacket(stats, socket, seq, buffer, stats.getTotalBytes());
				packet.setAddress(server);
				packet.setPort(lastPort);
				
				if (sendDatagramPacket(stats, socket, packet, seq)) {
					seq++;
					System.out.printf("packet %d sent\n", seq);
				}
			}
				
			System.out.printf("bytes sent %d \n file length %d\n", stats.getTotalBytes(), len);
			//stats.setTotalAcks(acks);
			//stats.setTotalBytes(bytesSent);
			//stats.setTotalDataBlocks(packetsSent);
			
		} catch (SocketException e) {
			e.printStackTrace();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}



	private static byte[] readFile(String fileName) throws Exception {
		//read the file to a byte array (fileArray)
		FileInputStream in = new FileInputStream(fileName);
		int totalBytes = 0;
		while (in.read() != -1)
			totalBytes++;
		in.close();
	
		byte[] fileArray = new byte[totalBytes];
		in = new FileInputStream(fileName);
		in.read(fileArray, 0, totalBytes);
		in.close();
		System.out.printf("file read: total bytes %d\n", totalBytes);
		return fileArray;
	}
	
	/*
	 * The method to return a datagram packet ready to be sent
	 *  with the file name request tftp packet encapsulated
	 */
	private static DatagramPacket prepareWriteRequest(DatagramSocket socket, String fileName, String mode) {

		try {
			//prepare tftp packet

			//create new tftp packet
			TFtpPacketV18 tftpPacket = new TFtpPacketV18(TFtpPacketV18.OpCode.OP_WRQ);

			//file name and mode to a byte array, each ending with a zero valued byte
			int n = fileName.length() + mode.length() + 2;
			byte[] buffer = new byte[n];

			//complete the filename, add the 0 value byte at the end of it and add the mode to the buffer
			for (int i = 0; i < buffer.length-1; i++) {
				if (i < fileName.length())
					buffer[i]= (byte) fileName.charAt(i);
				else if (i == fileName.length()) //end filename with zero valued byte
					buffer[i] = (byte) 0;
				else 
					buffer[i] = (byte) mode.charAt(i-(fileName.length()+1));
			}
			buffer[buffer.length-1] = (byte) 0; //end with a zero valued byte


			//put the buffer into the tftp packet
			tftpPacket.putBytes(buffer, buffer.length);

			//get the write request with the file name to a new udp packet
			DatagramPacket udpPacket = tftpPacket.toDatagramPacket(socket.getLocalSocketAddress());

			return udpPacket; //return the datagram packet

		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}

	}
	
	private static DatagramPacket prepareDataPacket(Stats stats, DatagramSocket socket, int seq, byte[] buffer, int bytesN) {
		
		int bytes = 0;

		//prepare tftp packet
		TFtpPacketV18 tftpPacket = new TFtpPacketV18(TFtpPacketV18.OpCode.OP_DATA);

		tftpPacket.putShort(seq); //put sequence number
		
		while(bytes < TFtpPacketV18.TFTP_BLOCK_SIZE && bytesN+bytes<buffer.length) {
			tftpPacket.putByte(buffer[bytesN+bytes]);
			bytes++;
			//update bytes sent
			stats.incTotalBytes();
		}
	
		
	
		return tftpPacket.toDatagramPacket(socket.getLocalSocketAddress());
	}
	

	private static DatagramPacket prepareEmptyPacket(DatagramSocket socket, int seq) {

		TFtpPacketV18 packet = new TFtpPacketV18(TFtpPacketV18.OpCode.OP_DATA);
		
		packet.putShort(seq);
		
		return packet.toDatagramPacket(socket.getLocalSocketAddress());
		
	}
	

	private static boolean sendDatagramPacket(Stats stats, DatagramSocket socket, DatagramPacket udpPacket, int seq) {

		boolean done = false;
		
		try {
			//keeps sending the packet until it receives the ack for that packet
			while (!done) {
				//send packet
				socket.send(udpPacket);
				//update number of packets sent (global variable)
				stats.incTotalDataBlocks();
				
				//check if the ack arrives and it is the right one 
				if (receiveAck(stats, socket, seq)) 
					done = true;
				else
					;
			}
			
		
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return done;
		
	}

	/*
	* wait for an ack to arrive at the socket, and check if it is the ack expected.
		if it is, returns true, 
			otherwise keeps waiting for the right ack to arrive for the DEFAULT TRIES number of times
	*/
	private static boolean receiveAck(Stats stats, DatagramSocket socket, int seq) {
		boolean done = false;
		int n = DEFAULT_TRIES;

		System.out.printf("waiting on ack ... ");

		try {
			
			while(true && n > 0) {

				//set timeout on socket
				socket.setSoTimeout(computeTimeout(nTimeouts));
				
				try {
					//create packet to receive the ack
					byte[] buffer = new byte[TFtpPacketV18.MAX_TFTP_DATAGRAM_SIZE];
					DatagramPacket ack = new DatagramPacket(buffer, buffer.length);
					
					//receive the ack (waits until timeout)
					socket.receive(ack);
					//update number of acks received (global variable)
					stats.incTotalAcks();
					
					//get tftp packet from the ack datagram buffer
					TFtpPacketV18 tftpAck = new TFtpPacketV18(buffer, buffer.length);
					//check if it is the ack we want
					if (checkACK(tftpAck, seq)) {
						lastPort = ack.getPort(); //set the last port to the port from which the ack came from
						done = true;
						break;
					}
					else  //loop again
						System.out.printf("received: %d expected: %d --", tftpAck.getBlockNumber(), seq);
					
				} catch (SocketTimeoutException e) {
					//update number of timeouts
					nTimeouts++;
					n--; 
				} 
				n--;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return done;
	}


	//main method
		public static void main(String[] args) throws Exception {
	    int port;
	    String fileName;
	    InetAddress server;

	    switch (args.length) {
	        case 3:
	            server = InetAddress.getByName(args[0]);
	            port = Integer.valueOf(args[1]);
	            fileName = args[2];
	            break;
	        default:
	            System.out.printf("usage: java %s server port filename\n", TFtp.class.getName());
	            return;
	    }

	    Stats stats = new Stats();
	    sendFile(stats, fileName, server, port);
	    stats.printReport();
	 }

}
