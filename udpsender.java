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
import java.net.SocketTimeoutException;
import java.io.File;

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
    static final int RETRYTIMES = 10;
    static final int MAXARRAYSIZE = 32767;
    static final int HEADERLENG = 7;
    static final int POSOFFRAMENUM = 6;
    static final int SOCKETTIMEOUT = 10;

    static int BUFFARRAYSIZE = MAXARRAYSIZE;
    static long BUFFSIZE = 256;
    // static String FILENAME = "super_big_test_file";
    // static String FILENAME = "extra_big_test_file";
    static String FILENAME = "medium_test_file";
    static long TIMEOUT = 20;
    static int MODE = 0;
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
        s.setMode(1);
        // s.setModeParameter(15000);
        s.sendFile();
    }

    public boolean setMode(int mode)
    {
        if (mode == 0 || mode == 1)
        {
            MODE = mode;
            return true;
        }
        return false;
    }
    public int getMode()
    {
        return MODE;
    }
    public boolean setModeParameter(long n)
    {
        if (MODE == 0)
        {
            return false;
        }
        BUFFSIZE = n;
        return true;
    }
    public long getModeParameter()
    {
        if (MODE == 0)
        {
            return 0;
        }
        return BUFFSIZE;
    }
    public void setFilename(String fname)
    {
        FILENAME = fname;
    }
    public String getFilename()
    {
        return FILENAME;
    }
    public boolean setTimeout(long timeout)
    {
        if (timeout > 0)
        {
            TIMEOUT = timeout;
            return true;
        }
        return false;
    }
    public long getTimeout()
    {
        return TIMEOUT;
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
            //set up socket
            UDPSocket socket = new UDPSocket(23456);
            socket.setSoTimeout(SOCKETTIMEOUT);
            //calculate framesize
            long send_buff_size = socket.getSendBufferSize();
            BUFFARRAYSIZE = (int)(send_buff_size - HEADERLENG);
            WINDOWSIZE = (int)Math.ceil((double)BUFFSIZE / send_buff_size);
            if (WINDOWSIZE > MAXARRAYSIZE)
                WINDOWSIZE = MAXARRAYSIZE;
            MAXFRAMENUM = (byte)((WINDOWSIZE*2) + 1);
            System.out.println(BUFFSIZE+" "+send_buff_size+" "+WINDOWSIZE+" "+MAXFRAMENUM);
            //set up file reader
            BufferedReader br = new BufferedReader(new FileReader(FILENAME));
            long file_size = (new File(FILENAME)).length();
            //set up packets
            byte [] buffer = new byte[BUFFARRAYSIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, InetAddress.getByName(SERVER), PORT);
            byte[] ack_buffer = new byte[HEADERLENG];
            DatagramPacket ack_packet = new DatagramPacket(ack_buffer,ack_buffer.length);
            byte[] readBuff = new byte[BUFFARRAYSIZE-HEADERLENG];
            //set up window buffers
            byte[][] sendWindowBuff = new byte[WINDOWSIZE][BUFFARRAYSIZE];
            long[] sendWindowTimes = new long[WINDOWSIZE];
            //misc vars
            boolean giveup = false;
            boolean done_read_file = false;
            byte framenum = 0;
            int lsf = -1;
            int outstanding_frames = 0;
            boolean send_ready = false;
            boolean eof_frame_sent = false;
            long bytes_sent = 0;
            long start_time = System.currentTimeMillis();

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
                    for (;bi<BUFFARRAYSIZE-HEADERLENG;bi++)
                    {
                        x = br.read();
                        if (x == -1)
                            break;
                        readBuff[bi] = (byte)x;
                    }
                    if (bi == 0)
                    {
                        if (!eof_frame_sent)
                        {
                            set_packet_length(buffer, 7);
                            buffer[4] = 3;
                            buffer[5] = 0x04;
                            buffer[POSOFFRAMENUM] = framenum;
                            done_read_file = true;
                            System.out.println("Done reading file");
                            lsf = (lsf+1); //the mod is only neccessary for size of 1 b/c shifting.
                            sendWindowBuff[lsf] = new byte[BUFFARRAYSIZE];
                            for (int i=0;i<BUFFARRAYSIZE;i++)
                                sendWindowBuff[lsf][i] = buffer[i];
                            sendWindowTimes[lsf] = System.currentTimeMillis();
                            outstanding_frames++;
                            send_ready = true;
                            eof_frame_sent = true;
                        }
                    }
                    else
                    {
                        set_packet_length(buffer, bi+4);
                        buffer[4] = 3;
                        buffer[5] = 0x00;
                        buffer[POSOFFRAMENUM] = framenum;
                        for(int j=0;j<readBuff.length;j++)
                        {
                            buffer[HEADERLENG+j] = (byte)readBuff[j];
                        }
                        lsf = (lsf+1); //the mod is only neccessary for size of 1 b/c shifting.
                        sendWindowBuff[lsf] = new byte[BUFFARRAYSIZE];
                        for (int i=0;i<BUFFARRAYSIZE;i++)
                            sendWindowBuff[lsf][i] = buffer[i];
                        sendWindowTimes[lsf] = System.currentTimeMillis();
                        outstanding_frames++;
                        send_ready = true;
                        bytes_sent += (BUFFARRAYSIZE-HEADERLENG);
                    }
                }
                try
                {
                    if (send_ready)
                    {
                        float perc_done = ((float)bytes_sent/file_size)*100;
                        System.out.format("%f sending %d\n", perc_done, buffer[POSOFFRAMENUM]);
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
                            for (int j=0;j<BUFFARRAYSIZE;j++)
                                buffer[j] = sendWindowBuff[i][j];
                            sendWindowTimes[i] = System.currentTimeMillis();
                            System.out.println("retransmitting "+ buffer[POSOFFRAMENUM]);
                            socket.send(packet);
                        }
                    }
                    for (int i=0;i<ack_buffer.length;i++)
                        ack_buffer[i] = 0;
                    socket.receive(ack_packet);
                    if (get_packet_length(ack_buffer)>0)
                    {
                        InetAddress client = ack_packet.getAddress();
                        byte ack_framenum = ack_buffer[POSOFFRAMENUM];
                        System.out.println("acked " + ack_framenum);
                        for (int j=0;j<WINDOWSIZE;j++)
                        {
                            if (sendWindowBuff[j]!=null && sendWindowBuff[j][POSOFFRAMENUM] == ack_framenum)
                            {
                                set_packet_length(sendWindowBuff[j], 0);
                            }
                        }
                        while(sendWindowBuff[0]!=null && get_packet_length(sendWindowBuff[0]) == 0)
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
                    }
                    if (sendWindowBuff[0] == null && done_read_file)
                    {
                        System.out.println("end of file");
                        giveup = true;
                    }
                }
                catch(SocketTimeoutException e)
                {
                }
                catch(IOException e)
                {
                    e.printStackTrace();
                    System.exit(1);
                }
            System.out.println(bytes_sent);
            long transmit_time = System.currentTimeMillis() - start_time;
            System.out.println(transmit_time);
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

    public static void set_packet_length(byte[] arry, int leng)
    {
        arry[0] = (byte)((leng >> 0x00) & 0xff);
        arry[1] = (byte)((leng >> 0x08) & 0xff);
        arry[2] = (byte)((leng >> 0x10) & 0xff);
        arry[3] = (byte)((leng >> 0x18) & 0xff);
    }
}
