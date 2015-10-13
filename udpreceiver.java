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

public class udpreceiver implements RReceiveUDPI{
    static final int PORT = 32456;
    static int TIMEOUT = 1000;
    static int RETRYTIMES = 30;
    static String FILENAME = "recieved_file";
    static int STARTOFHEADER = 5;
    static final int MAXARRAYSIZE = 32767;
    static final int HEADERLENG = 7;
    static final int POSOFFRAMENUM = 6;
    static final int WAITAFTEREOF = 2000; //how long after eof to wait to ack any lost frames.

    static int BUFFARRAYSIZE = MAXARRAYSIZE;
    static long BUFFSIZE = 256;
    static int MODE = 0;
    static int WINDOWSIZE = 5;
    static byte MAXFRAMENUM = 10;


    public static void main(String[] args)
    {
        udpreceiver r = new udpreceiver();
        r.setMode(1);
        r.setModeParameter(15000);
        r.receiveFile();
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
        boolean retVal = false;
        return retVal;
    }
    public long getModeParameter()
    {
        if (MODE == 0)
        {
            return 0;
        }
        return BUFFARRAYSIZE;
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
        boolean retVal = false;
        return retVal;
    }
    public int getLocalPort()
    {
        int retVal = 0;
        return retVal;
    }
    public boolean receiveFile()
    {
        try {
            //set up socket
            UDPSocket socket = new UDPSocket(PORT);
            socket.setSoTimeout(TIMEOUT);
            //set up window sizes
            long send_buff_size = socket.getSendBufferSize();
            BUFFARRAYSIZE = (int)(send_buff_size - HEADERLENG);
            WINDOWSIZE = (int)Math.ceil(BUFFSIZE / send_buff_size) + 1;
            if (WINDOWSIZE > MAXARRAYSIZE)
                WINDOWSIZE = MAXARRAYSIZE;
            MAXFRAMENUM = (byte)((WINDOWSIZE*2) + 1);
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
            //initialize windowbuffer
            for (int i=0;i<WINDOWSIZE;i++)
            {
                recWindowBuff[i] = null;
            }
            while(!((System.currentTimeMillis() - got_eof_time) > WAITAFTEREOF))
            {
                try
                {
                    socket.receive(packet);
                    InetAddress client = packet.getAddress();
                    int message_code =  buffer[5];
                    byte framenum =  buffer[POSOFFRAMENUM];
                    System.out.println("got "+framenum);
                    int offset;
                    if(message_code == 0x04)
                    {
                        System.out.println("End of transmission");
                        ack_buffer[POSOFFRAMENUM] = framenum;
                        socket.send(new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort()));
                        got_eof_time = System.currentTimeMillis();
                    }
                    else
                    {
                        offset = (framenum - lfnr);
                        if (offset<0)
                            offset += MAXFRAMENUM;
                        if (offset >= WINDOWSIZE)
                        {
                            System.out.println("Sender retransmitting acked frame: "+framenum);
                            ack_buffer[POSOFFRAMENUM] = framenum;
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                        }
                        else
                        {
                            recWindowBuff[offset] = Arrays.copyOf(buffer, buffer.length);
                            ack_buffer[POSOFFRAMENUM] = framenum;
                            System.out.println("Acking "+framenum);
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
            return true;
        }
        catch (FileNotFoundException e)
        {
            e.printStackTrace();
            return false;
        }
        catch(Exception e){ e.printStackTrace(); }
        System.out.println("This should never be printed");
        return false;
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
        int packet_length = get_packet_length(buffer);
        int header_length = get_header_length(buffer);
        return Arrays.copyOfRange(buffer, header_length + 4, packet_length+3);
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

}
