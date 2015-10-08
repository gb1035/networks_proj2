import java.net.DatagramPacket;
import edu.utulsa.unet.UDPSocket; //import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.IOException;
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
        boolean retVal = false;
        byte[] ack_buffer = new byte[1];
        ack_buffer[0] = (byte)0x06;
        boolean giveup = false;
        byte [] buffer = new byte[255];
        DatagramPacket packet = new DatagramPacket(buffer,buffer.length);
        int timeouts = 0;
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
                    int packet_length = buffer[0];
                    int header_length = buffer[1];
                    byte[] header = Arrays.copyOfRange(buffer, 2, header_length+1);
                    // System.out.println(packet_length);
                    // System.out.println(header_length);
                    // System.out.println(Arrays.toString(buffer));
                    // System.out.println(Arrays.toString(header));
                    int message_code = header[0];
                    byte framenum = header[1];
                    if(message_code == 0x04)
                    {
                        System.out.println("End of transmission");
                        socket.send(new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort()));
                        return true;
                    }
                    else
                    {
                        byte[] payload = Arrays.copyOfRange(buffer, header_length, packet_length-header_length);
                        System.out.print(framenum+":");
                        for (int i=0;i<(packet_length-header_length);i++)
                            System.out.print((char)payload[i]);
                    }
                    DatagramPacket ack_packet = new DatagramPacket(ack_buffer, ack_buffer.length, client, packet.getPort());
                    socket.send(ack_packet);
                    timeouts=0;
                }
                catch(IOException e)
                {
                    timeouts++;
                }
                if(timeouts >= RETRYTIMES)
                {
                    System.out.println("Reciever timing out.");
                    return false;
                }
            }
        }
        catch(Exception e){ e.printStackTrace(); }
        System.out.println("This should never be printed");
        return false;
    }
}
