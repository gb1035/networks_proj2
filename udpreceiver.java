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
    static int WINDOWSIZE = 6;
    static byte MAXFRAMENUM = 10;
    static String FILENAME = "recieved_file";
    static int STARTOFHEADER = 5;
    static final int MAXARRAYSIZE = 134217727;

    public static void main(String[] args)
    {
        udpreceiver e = new udpreceiver();
        e.receiveFile();
        // try
        // {
        //     byte [] buffer = new byte[11];
        //     UDPSocket socket = new UDPSocket(PORT);
        //     DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
        //     socket.receive(packet);
        //     InetAddress client = packet.getAddress();
        //     System.out.println(" Received'"+new String(buffer)+"' from " +packet.getAddress().getHostAddress()+" with sender port "+packet.getPort());
        // }
        // catch(Exception e){ e.printStackTrace(); }
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
            FileOutputStream fos = new FileOutputStream(FILENAME);
            boolean retVal = false;
            byte[] ack_buffer = new byte[7];
            ack_buffer[0] = (byte)0x04;
            ack_buffer[1] = (byte)0x00;
            ack_buffer[2] = (byte)0x00;
            ack_buffer[3] = (byte)0x00;
            ack_buffer[4] = (byte)0x02;
            ack_buffer[5] = (byte)0x06;
            ack_buffer[6] = (byte)0;
            boolean giveup = false;
            byte [] buffer = new byte[MAXARRAYSIZE];
            byte[][] recWindowBuff = new byte[WINDOWSIZE][MAXARRAYSIZE];
            DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
            int timeouts = 0;
            int lfr = -1;
            int lfnr = 0;
            //initialize windowbuffer
            for (int i=0;i<WINDOWSIZE;i++)
            {
                recWindowBuff[i] = null;
            }
            try
            {
                UDPSocket socket = new UDPSocket(PORT);
                socket.setSoTimeout(TIMEOUT);
                while(!giveup)
                {
                    try
                    {
                        socket.receive(packet);
                        InetAddress client = packet.getAddress();
                        int packet_length = get_packet_length(buffer);
                        int header_length = get_header_length(buffer);
                        byte[] header = Arrays.copyOfRange(buffer, STARTOFHEADER, header_length + STARTOFHEADER - 1);
                        int message_code = header[0];
                        byte framenum = header[1];
                        System.out.println("got "+framenum);
                        // for (int i=0;i<WINDOWSIZE;i++)
                        // {
                        //     if (recWindowBuff[i]!=null)
                        //         System.out.print(recWindowBuff[i][3]+" - ");
                        // }
                        // System.out.println();
                        int offset;
                        if(message_code == 0x04)
                        {
                            System.out.println("End of transmission");
                            if (WINDOWSIZE > 1)//purge the buffer
                            {
                                while(recWindowBuff[0]!=null && recWindowBuff[1]!=null)
                                {
                                    fos.write(get_payload(recWindowBuff[0]));
                                    for (int i=0;i<recWindowBuff.length-1;i++)
                                    {
                                        recWindowBuff[i]=recWindowBuff[i+1];
                                        lfr--;
                                    }
                                    recWindowBuff[recWindowBuff.length-1] = null;
                                }
                                fos.write(get_payload(recWindowBuff[0]));
                            }
                            ack_buffer[6] = framenum;
                            socket.send(new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort()));
                            fos.close();
                            return true;
                        }
                        byte[] payload = get_payload(buffer);
                        offset = (framenum - lfnr);
                        if (offset<0)
                            offset += MAXFRAMENUM;
                        // System.out.println(offset+" "+framenum+" "+lfnr);
                        if (offset >= WINDOWSIZE)
                        {
                            System.out.println("Sender retransmitting acked frame: "+framenum);
                            ack_buffer[6] = framenum;
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                        }
                        else
                        {
                            recWindowBuff[offset] = Arrays.copyOf(buffer, buffer.length);
                            ack_buffer[6] = framenum;
                            System.out.println("Acking "+framenum);
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                            timeouts=0;
                        }
                        if (WINDOWSIZE > 1)
                        {
                            while(recWindowBuff[0]!=null && recWindowBuff[1]!=null)
                            {
                                fos.write(get_payload(buffer));
                                for (int i=0;i<WINDOWSIZE-1;i++)
                                {
                                    recWindowBuff[i]=recWindowBuff[i+1];
                                }
                                lfnr = recWindowBuff[0][3];
                                lfr--;
                                recWindowBuff[recWindowBuff.length-1] = null;
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
            }
            catch(Exception e){ e.printStackTrace(); }
            System.out.println("This should never be printed");
            try
            {
                fos.close();
            }
            catch(IOException e){e.printStackTrace(); };
            return false;
        }
        catch (FileNotFoundException e)
        {
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
        int packet_length = buffer[0] & 0xFF;
        int header_length = buffer[1] & 0xFF;
        return Arrays.copyOfRange(buffer, header_length, packet_length);
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

}
