import java.net.DatagramPacket;
import edu.utulsa.unet.UDPSocket;
//import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

interface RSendUDPI {
    public boolean setMode(int mode);
    public int getMode();
    public boolean setModeParameter(long n);
    public long getModeParameter();
    public void setFilename(String fname);
    public String getFilename();
    public boolean setTimeout(long timeout);
    public long getTimeout();
    public boolean setLocalPort(int port);
    public int getLocalPort();
    public boolean setReceiver(InetSocketAddress receiver);
    public InetSocketAddress getReceiver();
    public boolean sendFile();
}

public class udpsender implements RSendUDPI{
    //static final String SERVER = "linux1.ens.utulsa.edu";
    static final String SERVER = "localhost";
    static final int PORT = 32456;
    // static String FILENAME = "big_test_file";
    static String FILENAME = "medium_test_file";
    static int TIMEOUT = 1000;
    static int RETRYTIMES = 10;
    static int WINDOWSIZE = 5;
    static byte MAXFRAMENUM = 10;

    public static void main(String[] args)
    {
        // try {
        //     byte [] buffer = ("Hello World- or rather Mauricio saying hello through UDP").getBytes();
        //     UDPSocket socket = new UDPSocket(23456);
        //     //DatagramSocket socket = new DatagramSocket(23456);
        //     socket.send(new DatagramPacket(buffer, buffer.length, InetAddress.getByName(SERVER), PORT));
        // }
        // catch(Exception e){ e.printStackTrace(); }
        udpsender s = new udpsender();
        s.sendFile();
    }

    public boolean setMode(int mode)
    {
        boolean retVal = false;
        return retVal;
    }
    public int getMode()
    {
        int retVal = 0;
        return retVal;
    }
    public boolean setModeParameter(long n)
    {
        boolean retVal = false;
        return retVal;
    }
    public long getModeParameter()
    {
        long retVal = 0;
        return retVal;
    }
    public void setFilename(String fname)
    {
        FILENAME = fname;
    }
    public String getFilename()
    {
        String retVal = "";
        return retVal;
    }
    public boolean setTimeout(long timeout)
    {
        boolean retVal = false;
        return retVal;
    }
    public long getTimeout()
    {
        long retVal = 0;
        return retVal;
    }
    public boolean setLocalPort(int port)
    {
        boolean retVal = false;
        return retVal;
    }
    public int getLocalPort()
    {
        int retVal = 0;
        return retVal;
    }
    public boolean setReceiver(InetSocketAddress receiver)
    {
        boolean retVal = false;
        return retVal;
    }
    public InetSocketAddress getReceiver()
    {
        InetSocketAddress retVal = new InetSocketAddress(PORT);
        return retVal;
    }
    public boolean sendFile()
    {
        try {
            BufferedReader br = new BufferedReader(new FileReader(FILENAME));
            byte [] buffer = new byte[255];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(SERVER), PORT);
            byte[] ack_buffer = new byte[7];
            DatagramPacket ack_packet = new DatagramPacket(ack_buffer,ack_buffer.length);
            UDPSocket socket = new UDPSocket(23456);
            socket.setSoTimeout(100);
            byte[] readBuff = new byte[251];
            byte[][] sendWindowBuff = new byte[WINDOWSIZE][256];
            long[] sendWindowTimes = new long[WINDOWSIZE];
            boolean giveup = false;
            boolean done_read_file = false;
            byte framenum = 0;
            int lsf = -1;
            int outstanding_frames = 0;
            boolean send_ready = false;
            for (int i=0;i<WINDOWSIZE;i++)
            {
                sendWindowBuff[i] = null;
                sendWindowTimes[i] = 0;
            }
            while (!giveup)
            {
                if (outstanding_frames < WINDOWSIZE)
                {
                    Arrays.fill(readBuff, (byte)0);
                    int bi=0;
                    int x;
                    for (;bi<251;bi++)
                    {
                        x = br.read();
                        if (x == -1)
                            break;
                        readBuff[bi] = (byte)x;
                    }
                    if (bi == 0)
                    {
                        buffer[0] = 4;
                        buffer[1] = 3;
                        buffer[2] = 0x04;
                        buffer[3] = framenum;
                        done_read_file = true;
                        System.out.println("Done reading file");
                        lsf = (lsf+1); //the mod is only neccessary for size of 1 b/c shifting.
                        sendWindowBuff[lsf] = new byte[256];
                        for (int i=0;i<255;i++)
                            sendWindowBuff[lsf][i] = buffer[i];
                        sendWindowTimes[lsf] = System.currentTimeMillis();
                        sendWindowBuff[lsf][255] = (byte)framenum;
                        outstanding_frames++;
                        send_ready = true;
                    }
                    else
                    {
                        buffer[0] = (byte)(bi+4);
                        buffer[1] = 3;
                        buffer[2] = 0x00;
                        buffer[3] = framenum;
                        for(int j=0;j<readBuff.length;j++)
                        {
                            buffer[4+j] = (byte)readBuff[j];
                        }
                        lsf = (lsf+1); //the mod is only neccessary for size of 1 b/c shifting.
                        sendWindowBuff[lsf] = new byte[256];
                        for (int i=0;i<255;i++)
                            sendWindowBuff[lsf][i] = buffer[i];
                        sendWindowTimes[lsf] = System.currentTimeMillis();
                        sendWindowBuff[lsf][255] = (byte)framenum;
                        outstanding_frames++;
                        send_ready = true;
                    }
                }
                try
                {
                    if (send_ready)
                    {
                        System.out.println("sending "+buffer[3]);
                        socket.send(packet);
                        framenum++;
                        framenum%=MAXFRAMENUM;
                        send_ready = false;
                    }
                    long curtime = System.currentTimeMillis();
                    for (int i=0;i<WINDOWSIZE;i++)
                    {
                        if (sendWindowBuff[i] != null && sendWindowBuff[i][0] != 0 && (curtime - sendWindowTimes[i])>TIMEOUT)
                        {
                            for (int j=0;j<255;j++)
                                buffer[j] = sendWindowBuff[i][j];
                            // System.out.println(Arrays.toString(buffer));
                            sendWindowTimes[i] = System.currentTimeMillis();
                            System.out.println("retransmitting "+ buffer[3]);
                            socket.send(packet);
                        }
                    }
                    for (int i=0;i<ack_buffer.length;i++)
                        ack_buffer[i] = 0;
                    socket.receive(ack_packet);
                    if (get_packet_length(ack_buffer)>0)
                    {
                        InetAddress client = ack_packet.getAddress();
                        byte ack_framenum = ack_buffer[6];
                        System.out.println("acked" + ack_framenum);
                        for (int j=0;j<WINDOWSIZE;j++)
                        {
                            if (sendWindowBuff[j]!=null && sendWindowBuff[j][255] == ack_framenum)
                            {
                                sendWindowBuff[j][0] = 0;
                            }
                        }
                        while(sendWindowBuff[0]!=null && sendWindowBuff[0][0] == 0)
                        {
                            for (int j=0;j<WINDOWSIZE-1;j++)
                            {
                                sendWindowBuff[j] = sendWindowBuff[j+1];
                                sendWindowTimes[j] = sendWindowTimes[j+1];
                            }
                            sendWindowBuff[WINDOWSIZE-1] = null;
                            sendWindowTimes[WINDOWSIZE-1] = 0;
                            lsf--;
                            outstanding_frames--;
                        }
                        // System.out.print(new String("ack"));
                    }
                    if (sendWindowBuff[0] == null && done_read_file)
                    {
                        System.out.println("end of file");
                        giveup = true;
                    }
                }
                catch(IOException e)
                {
                    // System.out.println("No ack in time, resending");
                    // if (i == RETRYTIMES)
                    // {
                    //     System.out.println("Too many timeouts, giving up.");
                    //     giveup = true;
                    //     return false;
                    // }
                }
            }
        }
        catch(FileNotFoundException e)
        {
            System.out.println("Error, file not found");
        }
        catch(IOException e)
        {
            System.out.println("IO Exception");
            e.printStackTrace();
        }
        return true;
    }

    public static int get_packet_length(byte[] arry)
    {
        int r = 0;
        r += (arry[0] & 0xff) << 0x00;
        r += (arry[1] & 0xff) << 0x08;
        r += (arry[2] & 0xff) << 0x10;
        r += (arry[3] & 0xff) << 0x18;
        return r;
    }
}
