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
            byte[] ack_buffer = new byte[4];
            ack_buffer[0] = (byte)0x04;
            ack_buffer[1] = (byte)0x02;
            ack_buffer[2] = (byte)0x06;
            ack_buffer[3] = (byte)0;
            boolean giveup = false;
            byte [] buffer = new byte[255];
            byte[][] recWindowBuff = new byte[WINDOWSIZE][255];
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
                        int packet_length = buffer[0] & 0xFF;
                        int header_length = buffer[1] & 0xFF;
                        byte[] header = Arrays.copyOfRange(buffer, 2, header_length+1);
                        // System.out.println(packet_length);
                        // System.out.println(header_length);
                        // System.out.println(Arrays.toString(buffer));
                        // System.out.println(Arrays.toString(header));
                        int message_code = header[0];
                        byte framenum = header[1];
                        System.out.println("got "+framenum);
                        for (int i=0;i<WINDOWSIZE;i++)
                        {
                            if (recWindowBuff[i]!=null)
                                System.out.print(recWindowBuff[i][3]+" - ");
                        }
                        System.out.println();
                        int offset;
                        if(message_code == 0x04)
                        {
                            System.out.println("End of transmission");
                            if (WINDOWSIZE > 1)//purge the buffer
                            {
                                // System.out.println(Arrays.toString(recWindowBuff[0])+Arrays.toString(recWindowBuff));
                                if (!(recWindowBuff[0]!=null && recWindowBuff[1]!=null))
                                {
                                    // System.out.println("No shift!!!!!!!!");
                                }
                                while(recWindowBuff[0]!=null && recWindowBuff[1]!=null)
                                {
                                    packet_length = recWindowBuff[0][0]&0xff;
                                    header_length = recWindowBuff[0][1]&0xff;
                                    fos.write(Arrays.copyOfRange(recWindowBuff[0], header_length+1, packet_length));
                                    for (int i=0;i<recWindowBuff.length-1;i++)
                                    {
                                        recWindowBuff[i]=recWindowBuff[i+1];
                                        lfr--;
                                        // System.out.println("SHIFTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                                    }
                                    recWindowBuff[recWindowBuff.length-1] = null;
                                }
                                packet_length = recWindowBuff[0][0]&0xff;
                                header_length = recWindowBuff[0][1]&0xff;
                                fos.write(Arrays.copyOfRange(recWindowBuff[0], header_length+1, packet_length));
                            }
                            ack_buffer[3] = framenum;
                            socket.send(new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort()));
                            fos.close();
                            return true;
                        }
                        byte[] payload = Arrays.copyOfRange(buffer, header_length+1, packet_length);
                        offset = (framenum - lfnr);
                        if (offset<0)
                            offset += MAXFRAMENUM;
                        System.out.println(offset+" "+framenum+" "+lfnr);
                        if (offset >= WINDOWSIZE)
                        {
                            System.out.println("Sender retransmitting acked frame: "+framenum);
                            ack_buffer[3] = framenum;
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                        }
                        else
                        {
                            recWindowBuff[offset] = Arrays.copyOf(buffer, buffer.length);
                            ack_buffer[3] = framenum;
                            System.out.println("Acking "+framenum);
                            DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                            socket.send(ack_packet);
                            timeouts=0;
                        }
                        if (WINDOWSIZE > 1)
                        {
                            // System.out.println(Arrays.toString(recWindowBuff[0])+Arrays.toString(recWindowBuff));
                            if (!(recWindowBuff[0]!=null && recWindowBuff[1]!=null))
                            {
                                // System.out.println("No shift!!!!!!!!");
                            }
                            while(recWindowBuff[0]!=null && recWindowBuff[1]!=null)
                            {
                                packet_length = recWindowBuff[0][0]&0xff;
                                header_length = recWindowBuff[0][1]&0xff;
                                fos.write(Arrays.copyOfRange(recWindowBuff[0], header_length+1, packet_length));
                                for (int i=0;i<WINDOWSIZE-1;i++)
                                {
                                    // System.out.println(Arrays.toString(recWindowBuff));
                                    recWindowBuff[i]=recWindowBuff[i+1];
                                }
                                lfnr = recWindowBuff[0][3];
                                lfr--;
                                // System.out.println("SHIFTING!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                                recWindowBuff[recWindowBuff.length-1] = null;
                                // System.out.println(Arrays.toString(recWindowBuff));
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
        System.out.println(Arrays.toString(buffer));
        int packet_length = buffer[0] & 0xFF;
        int header_length = buffer[1] & 0xFF;
        return Arrays.copyOfRange(buffer, header_length, packet_length);
    }

}
