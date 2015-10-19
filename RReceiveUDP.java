import java.net.DatagramPacket;
import edu.utulsa.unet.UDPSocket; //import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.util.Arrays;

interface RReceiveUDPI {
    public boolean setMode(int mode);
    public int getMode();
    public boolean setModeParameter(long n);
    public long getModeParameter();
    public void setFilename(String fname);
    public String getFilename();
    public boolean setLocalPort(int port);
    public int getLocalPort();
    public boolean receiveFile();
}

public class RReceiveUDP implements RReceiveUDPI{
    static int TIMEOUT = 1000;
    static int RETRYTIMES = 30;
    static String FILENAME = "received_file";
    static int STARTOFHEADER = 5;
    static final int MAXARRAYSIZE = 32767;
    static final int HEADERLENG = 7;
    static final int CRCLENG = 2;
    static final int POSOFFRAMENUM = 6;
    static final int WAITAFTEREOF = 2000; //how long after eof to wait to ack any lost frames.

    static int BUFFARRAYSIZE = MAXARRAYSIZE;
    static long BUFFSIZE = 256;
    static int MODE = 0;
    static int WINDOWSIZE = 5;
    static byte MAXFRAMENUM = 10;
    static int PORT = 32456;


    public static void main(String[] args)
    {
        // RReceiveUDP r = new RReceiveUDP();
        // r.setMode(1);
        // r.setModeParameter(15000);
        // r.receiveFile();

        RReceiveUDP receiver = new RReceiveUDP();
        receiver.setMode(1);
        receiver.setModeParameter(512);
        receiver.setFilename("less_important.txt");
        receiver.setLocalPort(32456);
        receiver.receiveFile();
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
    public boolean receiveFile()
    {
        try {
            //set up socket
            UDPSocket socket = new UDPSocket(PORT);
            socket.setSoTimeout(TIMEOUT);
            //set up window sizes
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
            WINDOWSIZE++; // to store the extra message
            // System.out.println(BUFFSIZE+" "+send_buff_size+" "+WINDOWSIZE+" "+MAXFRAMENUM);
            //set up the input file
            FileOutputStream fos = new FileOutputStream(FILENAME);
            boolean retVal = false;
            byte[] ack_buffer = new byte[7];
            set_packet_length(ack_buffer, 4);
            ack_buffer[4] = (byte)0x02;
            ack_buffer[5] = (byte)0x06;
            ack_buffer[POSOFFRAMENUM] = (byte)0;
            boolean giveup = false;
            byte [] buffer = new byte[BUFFARRAYSIZE];
            byte[][] recWindowBuff = new byte[WINDOWSIZE][BUFFARRAYSIZE];
            DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
            int timeouts = 0;
            int lfnr = 0;
            long got_eof_time = Long.MAX_VALUE;
            boolean banner = false;
            long bytes_received = 0;
            //initialize windowbuffer
            for (int i=0;i<WINDOWSIZE;i++)
            {
                recWindowBuff[i] = null;
            }
            while(!((System.currentTimeMillis() - got_eof_time) > WAITAFTEREOF))
            {
                try
                {
                    for(int i=0;i<buffer.length;i++)
                    {
                        buffer[i] = (byte)0;
                    }
                    socket.receive(packet);
                    InetAddress client = packet.getAddress();
                    if (!banner)
                    {
                        System.out.println("--------------------------------------------------");
                        System.out.format("Begin receiving file %s\n", FILENAME);
                        System.out.format("FROM %s\n", client);
                        System.out.format("Using %s\n", (MODE==0?"Stop-and-Wait":"sliding window"));
                        System.out.println("--------------------------------------------------");
                        banner = true;
                    }
                    int message_code =  buffer[5];
                    byte framenum =  buffer[POSOFFRAMENUM];
                    // System.out.println(Arrays.toString(buffer));
                    // System.out.println("Before--"+myToString(recWindowBuff));
                    int offset;
                    if(message_code == 0x04)
                    {

                        System.out.format("receiving message %d -EOF-\n",framenum);
                        ack_buffer[POSOFFRAMENUM] = framenum;
                        socket.send(new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort()));
                        got_eof_time = System.currentTimeMillis();
                        System.out.format("Acking message %d\n", framenum);
                    }
                    else
                    {
                        if(!check_crc_16(buffer))
                        {
                            System.out.println("----Message fails crc check----");
                            continue;
                        }
                        System.out.format("receiving message %d with %d bytes of actual data\n",framenum, get_packet_length(buffer)-(HEADERLENG+CRCLENG));
                        offset = (framenum - lfnr);
                        // if (offset<0)
                        //     offset += MAXFRAMENUM;
                        offset += MAXFRAMENUM; //Because in java -1 % 5 = -1 ... 
                        offset = offset % MAXFRAMENUM; //new
                        if (offset < 0 || offset >= WINDOWSIZE)//(offset >= WINDOWSIZE)
                        {
                            System.out.println("Sender retransmitting acked frame: "+framenum);
                            ack_buffer[POSOFFRAMENUM] = framenum;
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                        }
                        else
                        {
                            if (recWindowBuff[offset] == null)
                            {
                                bytes_received += get_packet_length(buffer)-(HEADERLENG+CRCLENG);
                                recWindowBuff[offset] = new byte[buffer.length]; //Arrays.copyOf(buffer, buffer.length);
                            }
                            for (int i=0;i<buffer.length;i++)
                            {
                                recWindowBuff[offset][i] = buffer[i];
                            }
                            ack_buffer[POSOFFRAMENUM] = framenum;
                            System.out.format("Acking message %d\n", framenum);
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                            timeouts=0;
                        }
                        if (WINDOWSIZE > 1)
                        {
                            while(recWindowBuff[0]!=null && recWindowBuff[1]!=null)
                            {
                                fos.write(get_payload(recWindowBuff[0]));
                                for (int i=0;i<WINDOWSIZE-1;i++)
                                {
                                    recWindowBuff[i]=recWindowBuff[i+1];
                                }
                                lfnr = recWindowBuff[0][POSOFFRAMENUM];
                                recWindowBuff[recWindowBuff.length-1] = null;
                            }
                        }
                        if (got_eof_time < Long.MAX_VALUE)
                        {
                            got_eof_time = System.currentTimeMillis();
                        }
                        // System.out.println("After--"+myToString(recWindowBuff));
                    }
                }
                catch(IOException e)
                {
                    timeouts++;
                }
                if(timeouts >= RETRYTIMES)
                {
                    System.out.println("Reciever timing out.");
                    fos.close();
                    return false;
                }
            }
            if (WINDOWSIZE > 1)
            {
                while(recWindowBuff[0]!=null && recWindowBuff[1]!=null)
                {
                    System.out.println("Flushing buffer");
                    fos.write(get_payload(recWindowBuff[0]));
                    for (int i=0;i<WINDOWSIZE-1;i++)
                    {
                        recWindowBuff[i]=recWindowBuff[i+1];
                    }
                    lfnr = recWindowBuff[0][POSOFFRAMENUM];
                    recWindowBuff[recWindowBuff.length-1] = null;
                }
                fos.write(get_payload(recWindowBuff[0]));
            }
            fos.close();
            System.out.println("--------------------------------------------------");
            System.out.format("Finished receiving file\n");
            System.out.format("Received %d bytes\n", bytes_received);
            System.out.println("--------------------------------------------------");
            return true;
        }
        catch (Exception e)
        {
            System.out.println("This should never be printed");
            e.printStackTrace();
            return false;
        }
    }

    public static boolean emptyBuffer(byte[][] arry)
    {
        for (byte[] i:arry)
        {
            if (i!=null)
            {
                return false;
            }
        }
        return true;
    }

    public static byte[] get_payload(byte[] buffer)
    {
        // System.out.println(Arrays.toString(buffer));
        int packet_length = get_packet_length(buffer);
        byte[] retVal = new byte[packet_length-(HEADERLENG+CRCLENG)];
        for (int i=0;i<retVal.length;i++)
        {
            retVal[i] = buffer[i+HEADERLENG];
        }
        return retVal;
        // return Arrays.copyOfRange(buffer, header_length + 4, packet_length-CRCLENG);
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

    public static int get_header_length(byte[] arry)
    {
        return arry[4];
    }

    public static void set_packet_length(byte[] arry, int leng)
    {
        arry[0] = (byte)((leng >> 0x00) & 0xff);
        arry[1] = (byte)((leng >> 0x08) & 0xff);
        arry[2] = (byte)((leng >> 0x10) & 0xff);
        arry[3] = (byte)((leng >> 0x18) & 0xff);
    }

    public static String myToString(byte[][] arry)
    {
        String retVal = "";
        for (byte[] i: arry)
        {
            if (i!=null)
                retVal += ""+i[POSOFFRAMENUM];
            else
                retVal += "null";
            retVal += ", ";
        }
        return retVal;
    }

    public static boolean check_crc_16(byte[] bytes)
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
        int msg_crc = 0;
        msg_crc += (bytes[bytes.length-CRCLENG+0] & 0xff) << 0x00;
        msg_crc += (bytes[bytes.length-CRCLENG+1] & 0xff) << 0x08;
        // System.out.println("CRC16 = " + Integer.toHexString(crc)+" - "+Integer.toHexString(msg_crc));
        return crc == msg_crc;
    }

}
