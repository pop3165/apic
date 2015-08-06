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

//import com.tailf.packages.ned.odl.Odl;

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
                
                	
	                ret_read = instr.read(buff);
	                if(ret_read > 0)
	                {
	                	System.out.println("read:" + new String(buff, 0, ret_read));
	                }
            	}while(checkServiceExist(instr, outstr) == false);
            	
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
                
                	Thread.sleep(2000); 
                
                	
	                ret_read = instr.read(buff);
	                if(ret_read > 0)
	                {
	                	System.out.println("read:" + new String(buff, 0, ret_read));
	                }
                }while(checkServiceExist(instr, outstr) == true)
                
                tc.disconnect();
            }
            catch(Exception e){
            	System.out.println(e);
            }
        
	}
	
	public static boolean checkServiceExist(InputStream in, OutputStream out) throws IOException, InterruptedException{
		
		out.write("end\n show running-config interface tenGigE 0/0/2/0\n".getBytes());
		out.flush();
		Thread.sleep(500);
		
		byte[] buff = new byte[1024];
		in.read(buff);
		
		String response = new String(buff);
		
		System.out.print("#######"+response);
		
		return response.contains("service")? true:false;
	}
	
	public static void main(String[] arg) throws Exception{
		//String test = "<input><node>pcc://110.0.0.1</node><name>update-tunnel</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative></lsp><path-setup-type xmlns=\"urn:opendaylight:params:xml:ns:yang:pcep:ietf:stateful\"><pst>1</pst></path-setup-type><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4></endpoints-obj><ero><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24003</sid><local-ip-address>13.0.0.1</local-ip-address><remote-ip-address>13.0.0.3</remote-ip-address></subobject><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24002</sid><local-ip-address>23.0.0.3</local-ip-address><remote-ip-address>23.0.0.2</remote-ip-address></subobject></ero></arguments><network-topology>pcep-topology</network-topology></input>";
		//String res = Odl.modify_sr(test);
		 
		//System.out.println(res); 
		
	}
	
	
	

}
