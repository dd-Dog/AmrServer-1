package com.dddog.server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedList;

import com.dddog.config.CommonConfig;
import com.dddog.packet.FramePacket;

//blog.csdn.net/zgyulongfei
//Email: zgyulongfei@gmail.com

public abstract class MediaServer {
	private final int mMaxClientNumber = CommonConfig.MAX_CLIENT_NUMBER;

	private DatagramSocket mUdpServer;
	private ServerSocket mTcpServer;
	private ArrayList<Client> mClientList;
	
	//接收的数据缓冲区
	private LinkedList<FramePacket> mPacketList;
	
	private int mBufferSize;

	private boolean mServerRunning;

	public MediaServer(int udpPort, int tcpPort, int bufferSize) {
		this.mBufferSize = bufferSize;
		try {
			mUdpServer = new DatagramSocket(udpPort);
			mTcpServer = new ServerSocket(tcpPort, mMaxClientNumber);
		} catch (Exception e) {
			// TODO: handle exception
		}
		mPacketList = new LinkedList<FramePacket>();
		mClientList = new ArrayList<Client>();
		mServerRunning = true;
	}

	public abstract void start();

	public void stop() {
		mServerRunning = false;

		if (mUdpServer != null) {
			mUdpServer.close();
			mUdpServer = null;
		}

		if (mTcpServer != null) {
			try {
				mTcpServer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mTcpServer = null;
		}

		mPacketList.clear();
		mPacketList = null;

		mClientList.clear();
		mClientList = null;
	}

	/**
	 * 收到客户端传过来的数据并写入到缓冲区
	 * @param packet
	 */
	public void addPacketToBuffer(FramePacket packet) {
		//如果缓冲区存储数据已经超过缓冲最大限度,删除最旧的FramePacket
		if (mPacketList.size() > mBufferSize) {
			takeAwayFirstPacket();
		}
		mPacketList.addLast(packet);
	}

	/**
	 * 获取缓存中第一个帧数据
	 * @return
	 */
	public byte[] takeAwayFirstFrame() {
		FramePacket packet = takeAwayFirstPacket();
		if (packet == null) {
			return null;
		}
		return packet.getFrame();
	}

	/**
	 * 删除第一个FramePacket
	 * @return
	 */
	private synchronized FramePacket takeAwayFirstPacket() {
		if (mPacketList.size() <= 0) {
			return null;
		}
		FramePacket fp = mPacketList.getFirst();
		if (fp == null) {
			return null;
		}
		FramePacket packet = new FramePacket(fp);
		mPacketList.removeFirst();
		return packet;
	}

	public void addClient(Socket clientSocket, int id) {
		mClientList.add(new Client(clientSocket, id));
	}

	public void removeClient(int id) {
		int needRemoveIndex = -1;
		for (int ix = 0; ix < mClientList.size(); ++ix) {
			Client client = mClientList.get(ix);
			if (client.getId() == id) {
				needRemoveIndex = ix;
				break;
			}
		}
		if (needRemoveIndex != -1) {
			mClientList.remove(needRemoveIndex);
		}
	}

	public DatagramSocket getUdpServer() {
		return mUdpServer;
	}

	public ServerSocket getTcpServer() {
		return mTcpServer;
	}

	public boolean isServerRunning() {
		return mServerRunning;
	}

	public boolean isBufferEmpty() {
		return mPacketList.size() == 0;
	}

	/**
	 * 多播线程，把数据转发给多个客户端
	 * @author bian
	 *
	 */
	public class MulticastThread implements Runnable {
		public void run() {
			while (isServerRunning()) {
				try {
					sendDataToAllClient();
					Thread.sleep(25);
				} catch (Exception e) {
				}
			}
		}

		public void sendDataToAllClient() throws Exception {
			if (isBufferEmpty() || mClientList.size() <= 0) {
				return;
			}
			boolean bufEmpty = isBufferEmpty();
			byte[] block = takeAwayFirstFrame();
			ArrayList<Integer> disConnectClient = new ArrayList<Integer>();
			//遍历所有的客户端Socket
			for (int ix = 0; ix < mClientList.size(); ++ix) {
				Client client = mClientList.get(ix);
				Socket clientSocket = client.getSocket();

				if (clientSocket.isConnected()) {
					try {
						if (!bufEmpty) {
							if (block == null) {
								continue;
							}
							OutputStream output = clientSocket.getOutputStream();
							output.write(block);
							output.flush();
						}
					} catch (Exception err) {
						disConnectClient.add(ix);
						System.out.println("send data to id=" + client.getId() + " error" + " :"
								+ err.getMessage());
					}
				} else {
					disConnectClient.add(ix);
				}
			}
			for (int ix = 0; ix < disConnectClient.size(); ++ix) {
				int index = disConnectClient.get(ix);
				mClientList.remove(index);
			}
			disConnectClient.clear();
			disConnectClient = null;
			block = null;
		}
	}

	/**
	 * 封装客户端的Socket，并分配id号
	 * @author bian
	 *
	 */
	private class Client {
		private Socket socket;
		private int id;

		public Client(Socket socket, int id) {
			this.socket = socket;
			this.id = id;
		}

		public Socket getSocket() {
			return socket;
		}

		public int getId() {
			return id;
		}
	}
}
