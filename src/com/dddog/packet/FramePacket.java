package com.dddog.packet;

//blog.csdn.net/zgyulongfei
//Email: zgyulongfei@gmail.com

public class FramePacket {
	byte[] frame;

	public FramePacket(FramePacket fp) {
		byte[] data = fp.getFrame();
		frame = new byte[data.length];
		System.arraycopy(data, 0, frame, 0, data.length);
	}

	public FramePacket(byte[] f, int len) {
		frame = new byte[len];
		System.arraycopy(f, 0, frame, 0, len);
	}

	public byte[] getFrame() {
		return frame;
	}
}