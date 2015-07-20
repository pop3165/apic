package com.example.qos;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.StringTokenizer;

import org.apache.commons.net.telnet.EchoOptionHandler;
import org.apache.commons.net.telnet.InvalidTelnetOptionException;
import org.apache.commons.net.telnet.SimpleOptionHandler;
import org.apache.commons.net.telnet.SuppressGAOptionHandler;
import org.apache.commons.net.telnet.TelnetClient;
import org.apache.commons.net.telnet.TelnetNotificationHandler;
import org.apache.commons.net.telnet.TerminalTypeOptionHandler;

public class NcsTelnet implements TelnetNotificationHandler{

    static TelnetClient tc = null;
    
	@Override
	public void receivedNegotiation(int arg0, int arg1) {
		// TODO Auto-generated method stub
		
	}
	
	public static void enablePBTS(){
		FileOutputStream fout = null;


        String remoteip = "10.79.44.244";

        int remoteport = 23;;

        try
        {
            fout = new FileOutputStream ("spy.log", true);
        }
        catch (IOException e)
        {
            System.err.println(
                "Exception while opening the spy file: "
                + e.getMessage());
        }

        tc = new TelnetClient();

        TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, false);
        EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
        SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(true, true, true, true);

        try
        {
            tc.addOptionHandler(ttopt);
            tc.addOptionHandler(echoopt);
            tc.addOptionHandler(gaopt);
        }
        catch (Exception e)
        {
            System.err.println("Error registering option handlers: " + e.getMessage());
        }

            try
            {
                tc.connect(remoteip, remoteport);


                tc.registerNotifHandler(new NcsTelnet());

                InputStream instr = tc.getInputStream();
                OutputStream outstr = tc.getOutputStream();
                
                
                
                byte[] buff = new byte[1024];
                int ret_read = 0;
                
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                
                outstr.write("admin\n".getBytes());
                outstr.flush();
                
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                outstr.write("cisco\n".getBytes());
                outstr.flush();
                
                
                
                Thread.sleep(500); 
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                {	
                	outstr.write("config t\n interface TenGigE0/0/2/0\n service-policy type pbr input E-PBR\n commit\n".getBytes());
                	outstr.flush();
                
                	Thread.sleep(2000); 
                }while(checkServiceExist(instr, outstr) == false)
                	
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                tc.disconnect();
            }
            catch(Exception e){
            	System.out.println(e);
            }
        
	}
	
	public static void disablePBTS(){
		FileOutputStream fout = null;


        String remoteip = "10.79.44.244";

        int remoteport = 23;;

        try
        {
            fout = new FileOutputStream ("spy.log", true);
        }
        catch (IOException e)
        {
            System.err.println(
                "Exception while opening the spy file: "
                + e.getMessage());
        }

        tc = new TelnetClient();

        TerminalTypeOptionHandler ttopt = new TerminalTypeOptionHandler("VT100", false, false, true, false);
        EchoOptionHandler echoopt = new EchoOptionHandler(true, false, true, false);
        SuppressGAOptionHandler gaopt = new SuppressGAOptionHandler(true, true, true, true);

        try
        {
            tc.addOptionHandler(ttopt);
            tc.addOptionHandler(echoopt);
            tc.addOptionHandler(gaopt);
        }
        catch (Exception e)
        {
            System.err.println("Error registering option handlers: " + e.getMessage());
        }

            try
            {
                tc.connect(remoteip, remoteport);


                tc.registerNotifHandler(new NcsTelnet());

                InputStream instr = tc.getInputStream();
                OutputStream outstr = tc.getOutputStream();
                
                
                
                byte[] buff = new byte[1024];
                int ret_read = 0;
                
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                
                outstr.write("admin\n".getBytes());
                outstr.flush();
                
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                outstr.write("cisco\n".getBytes());
                outstr.flush();
                
                
                
                Thread.sleep(500); 
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                {
                	outstr.write("config t\n interface TenGigE0/0/2/0\n no service-policy type pbr input E-PBR\n commit\n".getBytes());
                	outstr.flush();
                
                	Thread.sleep(2000); }
                while(checkServiceExist(instr, outstr) == true)
                	
                ret_read = instr.read(buff);
                if(ret_read > 0)
                {
                	System.out.println("read:" + new String(buff, 0, ret_read));
                }
                
                tc.disconnect();
            }
            catch(Exception e){
            	System.out.println(e);
            }
        
	}
	
	public static boolean checkServiceExist(InputStream in, OutputStream out) throws IOException, InterruptedException{
		
		out.write("show running-config interface tenGigE 0/0/2/0\n".getBytes());
		out.flush();
		Thread.sleep(500);
		
		byte[] buff = new byte[1024];
		in.read(buff);
		
		String response = buff.toString();
		
		return response.contains("service")? true:false;
	}
	
	public static void main(String[] arg){
		NcsTelnet.enablePBTS();
	}
	
	

}
