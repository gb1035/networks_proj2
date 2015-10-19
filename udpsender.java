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
    static final int RETRYTIMES = 10;
    static final int MAXARRAYSIZE = 32767;
    static final int HEADERLENG = 7;
    static final int CRCLENG = 2;
    static final int POSOFFRAMENUM = 6;
    static final int SOCKETTIMEOUT = 10;

    static int BUFFARRAYSIZE = MAXARRAYSIZE;
    static long BUFFSIZE = 256;
    // static String FILENAME = "super_big_test_file";
    static String FILENAME = "extra_big_test_file";
    // static String FILENAME = "pretty_big_test_file";
    // static String FILENAME = "big_test_file";
    // static String FILENAME = "medium_test_file";
    static long TIMEOUT = 10;
    static int MODE = 0;
    static int WINDOWSIZE = 5;
    static byte MAXFRAMENUM = 10;
    static int PORT = 12987;
    InetSocketAddress RECEIVER = new InetSocketAddress(32456);

    public static void main(String[] args)
    {
        // udpsender s = new udpsender();
        // s.setReceiver(new InetSocketAddress("localhost", 32456));
        // s.setMode(1);
        // s.setModeParameter(15000);
        // s.sendFile();

        // RSendUDP sender = new RSendUDP();
        // sender.setMode(1);
        // sender.setModeParameter(512);
        // sender.setTimeout(100);
        // sender.setFilename("important.txt");
        // sender.setLocalPort(23456);
        // sender.setReceiver(new InetSocketAddress("localhost", 32456));
        // sender.sendFile();
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
        if (port > 0 && port < 65535)
        {
            PORT = port;
            return true;
        }
        return false;
    }
    public int getLocalPort()
    {
        return PORT;
    }
    public boolean setReceiver(InetSocketAddress receiver)
    {
        RECEIVER = receiver;
        return true;
    }
    public InetSocketAddress getReceiver()
    {
        return RECEIVER;
    }
    public boolean sendFile()
    {
        try {
            //set up socket
            UDPSocket socket = new UDPSocket(PORT);
            socket.setSoTimeout(SOCKETTIMEOUT);
            //calculate framesize
            long send_buff_size = socket.getSendBufferSize();
            if (send_buff_size > BUFFSIZE)
            {
                send_buff_size = BUFFSIZE;
            }
            BUFFARRAYSIZE = (int)(send_buff_size);
            if (MODE == 0)
            {
                WINDOWSIZE = 1;
            }
            else
            {
                WINDOWSIZE = (int)Math.ceil((double)BUFFSIZE / send_buff_size);
                if (WINDOWSIZE > MAXARRAYSIZE)
                    WINDOWSIZE = MAXARRAYSIZE;
            }
            MAXFRAMENUM = (byte)((WINDOWSIZE*2)+2);
            // System.out.println(BUFFSIZE+" "+send_buff_size+" "+WINDOWSIZE+" "+MAXFRAMENUM);
            //set up file reader
            BufferedReader br = new BufferedReader(new FileReader(FILENAME));
            long file_size = (new File(FILENAME)).length();
            //set up packets
            byte [] buffer = new byte[BUFFARRAYSIZE];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, RECEIVER);
            byte[] ack_buffer = new byte[HEADERLENG];
            DatagramPacket ack_packet = new DatagramPacket(ack_buffer,ack_buffer.length);
            byte[] readBuff = new byte[BUFFARRAYSIZE-(HEADERLENG+CRCLENG)];
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
            int last_updated = 0;

            System.out.println("--------------------------------------------------");
            System.out.format("Begin sending file %s (%d bytes)\n", FILENAME, file_size);
            System.out.format("FROM %s:%d TO %s\n", SERVER, PORT, RECEIVER);
            System.out.format("Using %s\n", (MODE==0?"Stop-and-Wait":"sliding window"));
            System.out.println("--------------------------------------------------");

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
                    for (;bi<BUFFARRAYSIZE-(HEADERLENG+CRCLENG);bi++)
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
                        set_packet_length(buffer, bi+HEADERLENG+CRCLENG);
                        buffer[4] = 3;
                        buffer[5] = 0x00;
                        buffer[POSOFFRAMENUM] = framenum;
                        for(int j=0;j<bi;j++)
                        {
                            buffer[HEADERLENG+j] = readBuff[j];
                        }
                        set_crc_16(buffer);
                        lsf = (lsf+1); //the mod is only neccessary for size of 1 b/c shifting.
                        sendWindowBuff[lsf] = new byte[BUFFARRAYSIZE];
                        for (int i=0;i<BUFFARRAYSIZE;i++)
                            sendWindowBuff[lsf][i] = buffer[i];
                        sendWindowTimes[lsf] = System.currentTimeMillis();
                        outstanding_frames++;
                        send_ready = true;
                        bytes_sent += bi;
                    }
                }
                try
                {
                    if (send_ready)
                    {
                        float perc_done = ((float)bytes_sent/file_size)*100;
                        if (buffer[5] == 0x00)
                        {
//                            System.out.format("sending message %d with %d bytes of actual data\n", buffer[POSOFFRAMENUM], get_packet_length(buffer)-(HEADERLENG+CRCLENG));
                        }
                        else
                        {
                            System.out.format("sending message %d -EOF-\n", buffer[POSOFFRAMENUM]);
                        }
                        if (perc_done > last_updated+1)
                        {
                            last_updated = (int)Math.floor(perc_done);
                            System.out.format("%d%% finished with the transmission\n", last_updated);
                        }
                        socket.send(packet);
                        framenum++;
                        framenum%=MAXFRAMENUM;
                        send_ready = false;
                    }
                    long curtime = System.currentTimeMillis();
                    for (int i=0;i<WINDOWSIZE;i++)
                    {
                        if (sendWindowBuff[i] != null && get_packet_length(sendWindowBuff[i]) != 0 && (curtime - sendWindowTimes[i])>TIMEOUT)
                        {
                            for (int j=0;j<BUFFARRAYSIZE;j++)
                                buffer[j] = sendWindowBuff[i][j];
                            sendWindowTimes[i] = System.currentTimeMillis();
//                            System.out.println("retransmitting "+ buffer[POSOFFRAMENUM]);
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
//                        System.out.println("acked " + ack_framenum);  //--------------------------------------------------
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
                        System.out.println("Finished transmitting all frames");
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
            }
            double transmit_time = (System.currentTimeMillis() - start_time)/1000.0;
            double transrate = bytes_sent / transmit_time;
            String transrateunit = "b";
            if (transrate > 1024)
            {
                transrate = transrate / 1024;
                transrateunit = "Kb";
            }
            if (transrate > 1024)
            {
                transrate = transrate / 1024;
                transrateunit = "Mb";
            }
            if (transrate > 1024)
            {
                transrate = transrate / 1024;
                transrateunit = "Gb";
            }
            System.out.println("--------------------------------------------------");
            System.out.println("Finished sending file");
            System.out.format("Sent %d bytes in %.3f seconds (%.2f %s/s)\n", bytes_sent, transmit_time, transrate, transrateunit);
            System.out.println("--------------------------------------------------");
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

    public static void set_crc_16(byte[] bytes)
    {
        int[] table = {
            0x0000, 0xC0C1, 0xC181, 0x0140, 0xC301, 0x03C0, 0x0280, 0xC241,
            0xC601, 0x06C0, 0x0780, 0xC741, 0x0500, 0xC5C1, 0xC481, 0x0440,
            0xCC01, 0x0CC0, 0x0D80, 0xCD41, 0x0F00, 0xCFC1, 0xCE81, 0x0E40,
            0x0A00, 0xCAC1, 0xCB81, 0x0B40, 0xC901, 0x09C0, 0x0880, 0xC841,
            0xD801, 0x18C0, 0x1980, 0xD941, 0x1B00, 0xDBC1, 0xDA81, 0x1A40,
            0x1E00, 0xDEC1, 0xDF81, 0x1F40, 0xDD01, 0x1DC0, 0x1C80, 0xDC41,
            0x1400, 0xD4C1, 0xD581, 0x1540, 0xD701, 0x17C0, 0x1680, 0xD641,
            0xD201, 0x12C0, 0x1380, 0xD341, 0x1100, 0xD1C1, 0xD081, 0x1040,
            0xF001, 0x30C0, 0x3180, 0xF141, 0x3300, 0xF3C1, 0xF281, 0x3240,
            0x3600, 0xF6C1, 0xF781, 0x3740, 0xF501, 0x35C0, 0x3480, 0xF441,
            0x3C00, 0xFCC1, 0xFD81, 0x3D40, 0xFF01, 0x3FC0, 0x3E80, 0xFE41,
            0xFA01, 0x3AC0, 0x3B80, 0xFB41, 0x3900, 0xF9C1, 0xF881, 0x3840,
            0x2800, 0xE8C1, 0xE981, 0x2940, 0xEB01, 0x2BC0, 0x2A80, 0xEA41,
            0xEE01, 0x2EC0, 0x2F80, 0xEF41, 0x2D00, 0xEDC1, 0xEC81, 0x2C40,
            0xE401, 0x24C0, 0x2580, 0xE541, 0x2700, 0xE7C1, 0xE681, 0x2640,
            0x2200, 0xE2C1, 0xE381, 0x2340, 0xE101, 0x21C0, 0x2080, 0xE041,
            0xA001, 0x60C0, 0x6180, 0xA141, 0x6300, 0xA3C1, 0xA281, 0x6240,
            0x6600, 0xA6C1, 0xA781, 0x6740, 0xA501, 0x65C0, 0x6480, 0xA441,
            0x6C00, 0xACC1, 0xAD81, 0x6D40, 0xAF01, 0x6FC0, 0x6E80, 0xAE41,
            0xAA01, 0x6AC0, 0x6B80, 0xAB41, 0x6900, 0xA9C1, 0xA881, 0x6840,
            0x7800, 0xB8C1, 0xB981, 0x7940, 0xBB01, 0x7BC0, 0x7A80, 0xBA41,
            0xBE01, 0x7EC0, 0x7F80, 0xBF41, 0x7D00, 0xBDC1, 0xBC81, 0x7C40,
            0xB401, 0x74C0, 0x7580, 0xB541, 0x7700, 0xB7C1, 0xB681, 0x7640,
            0x7200, 0xB2C1, 0xB381, 0x7340, 0xB101, 0x71C0, 0x7080, 0xB041,
            0x5000, 0x90C1, 0x9181, 0x5140, 0x9301, 0x53C0, 0x5280, 0x9241,
            0x9601, 0x56C0, 0x5780, 0x9741, 0x5500, 0x95C1, 0x9481, 0x5440,
            0x9C01, 0x5CC0, 0x5D80, 0x9D41, 0x5F00, 0x9FC1, 0x9E81, 0x5E40,
            0x5A00, 0x9AC1, 0x9B81, 0x5B40, 0x9901, 0x59C0, 0x5880, 0x9841,
            0x8801, 0x48C0, 0x4980, 0x8941, 0x4B00, 0x8BC1, 0x8A81, 0x4A40,
            0x4E00, 0x8EC1, 0x8F81, 0x4F40, 0x8D01, 0x4DC0, 0x4C80, 0x8C41,
            0x4400, 0x84C1, 0x8581, 0x4540, 0x8701, 0x47C0, 0x4680, 0x8641,
            0x8201, 0x42C0, 0x4380, 0x8341, 0x4100, 0x81C1, 0x8081, 0x4040,
        };
        int crc = 0x0000;
        for (int i=0;i<get_packet_length(bytes)-CRCLENG;i++) {
            crc = (crc >>> 8) ^ table[(crc ^ bytes[i]) & 0xff];
        }
        bytes[(bytes.length-CRCLENG)+0] = (byte)((crc >> 0x00) & 0xff);
        bytes[(bytes.length-CRCLENG)+1] = (byte)((crc >> 0x08) & 0xff);

        // System.out.println("CRC16 = " + Integer.toHexString(crc));
    }

}
