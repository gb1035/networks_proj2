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
    static String FILENAME = "test_file";
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
            byte[] ack_buffer = new byte[1];
            DatagramPacket ack_packet = new DatagramPacket(ack_buffer,ack_buffer.length);
            UDPSocket socket = new UDPSocket(23456);
            socket.setSoTimeout(TIMEOUT);
            byte []readBuff = new byte[251];
            boolean giveup = false;
            byte framenum = 0;
            while (!giveup)
            {
                Arrays.fill(readBuff, (byte)0);
                int bi=0;
                int x;
                for (;bi<252;bi++)
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
                }
                for(int i=1;i<=RETRYTIMES;i++)
                {
                    try
                    {
                        socket.send(packet);
                        socket.receive(ack_packet);
                        InetAddress client = ack_packet.getAddress();
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
