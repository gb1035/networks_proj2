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
            byte[] ack_buffer = new byte[4];
            DatagramPacket ack_packet = new DatagramPacket(ack_buffer,ack_buffer.length);
            UDPSocket socket = new UDPSocket(23456);
            socket.setSoTimeout(TIMEOUT);
            byte[] readBuff = new byte[251];
            byte[][] sendWindowBuff = new byte[WINDOWSIZE][256];
            boolean giveup = false;
            byte framenum = 0;
            int lsf = -1;
            int outstanding_frames = 0;
            for (int j=0;j<WINDOWSIZE;j++)
            {
                sendWindowBuff[j] = null;
            }
            while (!giveup)
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
                    giveup = true;
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
                    sendWindowBuff[lsf][255] = (byte)framenum;
                }
                for(int i=1;i<=RETRYTIMES;i++)
                {
                    try
                    {
                        ack_buffer[0]=0;
                        socket.send(packet);
                        socket.receive(ack_packet);
                        InetAddress client = ack_packet.getAddress();
                        byte ack_framenum = ack_buffer[3];
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
                            }
                            sendWindowBuff[WINDOWSIZE-1] = null;
                            lsf--;
                        }
                        // System.out.print(new String("ack"));
                        framenum++;
                        framenum%=MAXFRAMENUM;
                        break;
                    }
                    catch(IOException e)
                    {
                        System.out.println("No ack in time, resending");
                        if (i == RETRYTIMES)
                        {
                            System.out.println("Too many timeouts, giving up.");
                            giveup = true;
                            return false;
                        }
                        continue;
                    }
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
}
