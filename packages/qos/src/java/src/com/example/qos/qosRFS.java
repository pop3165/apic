package com.example.qos;

import com.example.qos.namespaces.*;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import org.w3c.dom.Document;

import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;


public class qosRFS{

	
	String src_device_name = "";
	String src_endpoint = "";
	String src_port = "";
	String dst_device_name = "";
	String dst_endpoint = "";
	String dst_port = "";
	String qos_level = "";
	
	String apic_tenant = "";
	String apic_application_profile = "";
	String apic_end_point = "";
	
	String odl_node = "";
	String lspName="tencentlsp";
	Integer tunnelNumber;
	
	//OdlEventsClient odlEventsClient;
	
	enum LspType{ SegmentRouting, RSVP};
	
	static LspType lt = LspType.SegmentRouting;
	
	//we use 
	public qosRFS(){
		System.out.println("############ Service Initiled");
		//odlEventsClient = odlEventsClient.getInstance();
	}

    /**
     * Create callback method.
     * This method is called when a service instance committed due to a create
     * or update event.
     *
     * This method returns a opaque as a Properties object that can be null.
     * If not null it is stored persistently by Ncs.
     * This object is then delivered as argument to new calls of the create
     * method for this service (fastmap algorithm).
     * This way the user can store and later modify persistent data outside
     * the service model that might be needed.
     *
     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws DpCallbackException
     */

    @ServiceCallback(servicePoint="qos-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws DpCallbackException {

        try {
            // check if it is reasonable to assume that devices
            // initially has been sync-from:ed
            NavuList managedDevices = ncsRoot.
                container("devices").list("device");
            for (NavuContainer device : managedDevices) {
                if (device.list("capability").isEmpty()) {
                    String mess = "Device %1$s has no known capabilities, " +
                                   "has sync-from been performed?";
                    String key = device.getKey().elementAt(0).toString();
                    throw new DpCallbackException(String.format(mess, key));
                }
            }
        } catch (DpCallbackException e) {
            throw (DpCallbackException) e;
        } catch (Exception e) {
            throw new DpCallbackException("Not able to check devices", e);
        }


        String servicePath = null;
        try {
            servicePath = service.getKeyPath();

            //Now get the single leaf we have in the service instance
            // NavuLeaf sServerLeaf = service.leaf("dummy");

            //..and its value (wich is a ipv4-addrees )
            // ConfIPv4 ip = (ConfIPv4)sServerLeaf.value();

            //Get the list of all managed devices.
            NavuList managedDevices =
                ncsRoot.container("devices").list("device");

            // iterate through all manage devices
            for(NavuContainer deviceContainer : managedDevices.elements()){
                // here we have the opportunity to do something with the
                // ConfIPv4 ip value from the service instance,
                // assume the device model has a path /xyz/ip, we could
                // deviceContainer.container("config").
                //         .container("xyz").leaf(ip).set(ip);
                //
                // remember to use NAVU sharedCreate() instead of
                // NAVU create() when creating structures that may be
                // shared between multiple service instances
            }
            try {
            	
				src_device_name = service.leaf("src-device-name").valueAsString();
				src_endpoint = service.leaf("src-endpoint").valueAsString();
				src_port = service.leaf("src-port").valueAsString();
				dst_device_name = service.leaf("dst-device-name").valueAsString();
				dst_endpoint = service.leaf("dst-endpoint").valueAsString();
				dst_port = service.leaf("dst-port").valueAsString();
				qos_level = service.leaf("qos-level").valueAsString();
				
				apic_tenant = "win_cisco";
				apic_application_profile = src_endpoint.equals("")?"ap1":src_endpoint;
				apic_end_point = src_port.equals("")?"ep1":src_port;
				
				odl_node ="pcc://110.0.0.1";
				
				
				if(src_device_name.contains("apic")){
					NavuLeaf deviceLeaf = service.leaf("src-device-name");
					NavuContainer device=(NavuContainer)deviceLeaf.deref().get(0).getParent();
					
					//Check QOS_LEVEL Before set
					String prio = device.container("config").container("cisco-apicdc","apic").list("fvTenant").elem(apic_tenant).list("fvAp").elem(apic_application_profile).list("fvAEPg").elem(apic_end_point).leaf("prio").valueAsString();
					System.out.println("Before QOS service"+prio);
					
					//set QOS_LEVEL to APIC
					device.container("config").container("cisco-apicdc","apic").list("fvTenant").elem(apic_tenant).list("fvAp").elem(apic_application_profile).list("fvAEPg").elem(apic_end_point).leaf("prio").set(qos_level);
					
					//Check QOS_LEVEL after set
					prio = device.container("config").container("cisco-apicdc","apic").list("fvTenant").elem(apic_tenant).list("fvAp").elem(apic_application_profile).list("fvAEPg").elem(apic_end_point).leaf("prio").valueAsString();
					System.out.println("After QOS service"+prio);
				}
				
				
				if(dst_device_name.contains("odlc")){
					NavuLeaf deviceLeaf = service.leaf("dst-device-name");
					NavuContainer device=(NavuContainer)deviceLeaf.deref().get(0).getParent();
					
					
					
					switch(lt){
					case RSVP:
						//add-lsp, R1->R3->R2, next hop 13.0.0.3, 23.0.0.2
						device.container("rpc").container("odl", "rpc-add-lsp").action("add-lsp").call("<node>"+odl_node+"</node><name>"+lspName+"</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative> </lsp><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4> </endpoints-obj><ero><subobject><loose>false</loose><ip-prefix><ip-prefix>13.0.0.3/32</ip-prefix></ip-prefix></subobject><subobject><loose>false</loose><ip-prefix><ip-prefix>23.0.0.2/32</ip-prefix></ip-prefix></subobject></ero> </arguments><network-topology>pcep-topology</network-topology>");
						break;
						
					case SegmentRouting:
						device.container("rpc").container("odl", "rpc-add-lsp-sr").action("add-lsp-sr").call("<node>"+odl_node+"</node><name>"+lspName+"</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative></lsp><path-setup-type><pst>1</pst></path-setup-type><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4></endpoints-obj><ero><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24003</sid><local-ip-address>13.0.0.1</local-ip-address><remote-ip-address>13.0.0.3</remote-ip-address></subobject><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24002</sid><local-ip-address>23.0.0.3</local-ip-address><remote-ip-address>23.0.0.2</remote-ip-address></subobject></ero></arguments><network-topology>pcep-topology</network-topology>");
					}
					
					
					//Get tunnelNumber of certain LSP by @lspName
					getTunnelNumber(device);
					
				}
				
				//Steer traffic to certain tunnel
				NavuContainer device = managedDevices.elem("a9k");
				addTrafficSteer(device);
				
				//devices device a9k config cisco-ios-xr:router static address-family ipv4 unicast 88.88.88.88/32 tunnel-te1
				//enable pbts on R1
				device.container("config").container("cisco-ios-xr", "interface").list("TenGigE").elem("0/0/2/0").container("service-policy").container("type").container("pbr").leaf("input").set("E-PBR");
				
            }
            catch(ConfException e){
            	System.out.println(e);
            }
        } catch (NavuException e) {
        	System.out.println(e);
            throw new DpCallbackException("Cannot create service " +
                                          servicePath, e);          
        }
        return opaque;
    }


    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="qos-self-test", callType=ActionCBType.INIT)
    public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="qos-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "qos";
            // Get the service instance key
            String str = ((ConfKey)kp[0]).toString();

          return new ConfXMLParam[] {
              new ConfXMLParamValue(nsPrefix, "success", new ConfBool(true)),
              new ConfXMLParamValue(nsPrefix, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("self-test failed", e);
        }
    }
    

	@ServiceCallback(servicePoint = "qos-servicepoint",
	                 callType = ServiceCBType.PRE_MODIFICATION)
	public Properties preModification(ServiceContext context, ServiceOperationType operation,
	ConfPath path,
	Properties opaque)
	throws DpCallbackException{
		
		return opaque;
		
	}
	
	//we are just using post modification to modify the lsp
	@ServiceCallback(servicePoint = "qos-servicepoint",
	                 callType = ServiceCBType.POST_MODIFICATION)
	public Properties postModification(ServiceContext context, ServiceOperationType operation,
	ConfPath path,
	Properties opaque)
	throws DpCallbackException{
		
		try{
			NavuList managedDevices =context.getRootNode().container("devices").list("device");
	        NavuContainer odlController=managedDevices.elem("odlc");
	        NavuContainer a9k = managedDevices.elem("a9k");
	        
	        //ConfBool isNotified = (ConfBool) context.getRootNode().container("services").list("qos", "qos").elem("c_qos").leaf("isNotified").value();
	        ConfBool isNotified = new ConfBool(false);
	        
	        
	        if(ServiceOperationType.DELETE == operation){
				System.out.println("Delete ---- Modification");
				
	                //Get tunnelNumber of certain LSP by @lspName
					getTunnelNumber(odlController);
					
					//remove traffic steering static routes
					removeTrafficSteer(a9k);
					
	                switch(lt){
	                case RSVP:
	                	odlController.container("rpc").container("odl", "rpc-remove-lsp").action("remove-lsp").call("<node>"+odl_node+"</node><name>"+lspName+"</name><network-topology>pcep-topology</network-topology>");
	                	break;
	                	
	                case SegmentRouting:
	                	odlController.container("rpc").container("odl", "rpc-remove-lsp-sr").action("remove-lsp-sr").call("<node>"+odl_node+"</node><name>"+lspName+"</name><network-topology>pcep-topology</network-topology>");
	                	break;
	                }
	                
	                a9k.container("config").container("cisco-ios-xr", "interface").list("TenGigE").elem("0/0/2/0").container("service-policy").container("type").container("pbr").leaf("input").delete();
			}
			
	        //northbound update without topology change events
			if(ServiceOperationType.UPDATE == operation && isNotified.booleanValue() == false){
				System.out.println("Update ---- Modification");
	                if(qos_level.equals("level1")){
	                	
	                	switch(lt){
	                	case RSVP:
	                		
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//remove traffic steering static routes
	        				removeTrafficSteer(a9k);
	        				
	                		System.out.println("delete old(level2) lsp");
	                		odlController.container("rpc").container("odl", "rpc-remove-lsp").action("remove-lsp").call("<node>"+odl_node+"</node><name>"+lspName+"</name><network-topology>pcep-topology</network-topology>");
	        				
	                		//add lsp, R1->R2,next hop 12.0.0.2
	                		System.out.println("create new(level1) lsp");
	                		odlController.container("rpc").container("odl", "rpc-add-lsp").action("add-lsp").call("<node>"+odl_node+"</node><name>"+lspName+"</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative> </lsp><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4> </endpoints-obj><ero><subobject><loose>false</loose><ip-prefix><ip-prefix>12.0.0.2/32</ip-prefix></ip-prefix></subobject></ero> </arguments><network-topology>pcep-topology</network-topology>");		
	                		
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//add traffic steering static routes
	        				addTrafficSteer(a9k);
	                		break;
	                	case SegmentRouting:
	                		
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//remove traffic steering static routes
	        				removeTrafficSteer(a9k);
	        				
	                		System.out.println("delete old(level2) lsp-sr");
	                		odlController.container("rpc").container("odl", "rpc-remove-lsp-sr").action("remove-lsp-sr").call("<node>"+odl_node+"</node><name>"+lspName+"</name><network-topology>pcep-topology</network-topology>");
	                        
	                		//add lsp-sr, R1->R2,next hop 12.0.0.2
	                		System.out.println("create new(level1) lsp-sr");
	                		odlController.container("rpc").container("odl", "rpc-add-lsp-sr").action("add-lsp-sr").call("<node>"+odl_node+"</node><name>"+lspName+"</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative></lsp><path-setup-type><pst>1</pst></path-setup-type><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4></endpoints-obj><ero><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24001</sid><local-ip-address>12.0.0.1</local-ip-address><remote-ip-address>12.0.0.2</remote-ip-address></subobject></ero></arguments><network-topology>pcep-topology</network-topology>");
	                		
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//add traffic steering static routes
	        				addTrafficSteer(a9k);
	                		break;
	                	}
	                }
	                
	                if(qos_level.equals("level2")){
	                	
	                	switch(lt){
	                	case RSVP:
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//remove traffic steering static routes
	        				removeTrafficSteer(a9k);
	        				
		                	System.out.println("delete old(level1) lsp");
		                    odlController.container("rpc").container("odl", "rpc-remove-lsp").action("remove-lsp").call("<node>"+odl_node+"</node><name>"+lspName+"</name><network-topology>pcep-topology</network-topology>");
	
		                    System.out.println("create new(level2) lsp");
		                    //add-lsp, R1->R3->R2, next hop 13.0.0.3, 23.0.0.2
		                    odlController.container("rpc").container("odl", "rpc-add-lsp").action("add-lsp").call("<node>"+odl_node+"</node><name>"+lspName+"</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative> </lsp><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4> </endpoints-obj><ero><subobject><loose>false</loose><ip-prefix><ip-prefix>13.0.0.3/32</ip-prefix></ip-prefix></subobject><subobject><loose>false</loose><ip-prefix><ip-prefix>23.0.0.2/32</ip-prefix></ip-prefix></subobject></ero> </arguments><network-topology>pcep-topology</network-topology>");
		                    
		                    //Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//add traffic steering static routes
	        				addTrafficSteer(a9k);
		                    break;
	                	case SegmentRouting:
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//remove traffic steering static routes
	        				removeTrafficSteer(a9k);
	        				
	                		System.out.println("delete old(level1) lsp-sr");
	                		odlController.container("rpc").container("odl", "rpc-remove-lsp-sr").action("remove-lsp-sr").call("<node>"+odl_node+"</node><name>"+lspName+"</name><network-topology>pcep-topology</network-topology>");
	                        
	        				//add lsp-sr, R1->R3->R2, next hop 13.0.0.3, 23.0.0.2
	                		System.out.println("create new(level2) lsp-sr");
	                		odlController.container("rpc").container("odl", "rpc-add-lsp-sr").action("add-lsp-sr").call("<node>"+odl_node+"</node><name>"+lspName+"</name><arguments><lsp><delegate>true</delegate><administrative>true</administrative></lsp><path-setup-type><pst>1</pst></path-setup-type><endpoints-obj><ipv4><source-ipv4-address>1.1.1.1</source-ipv4-address><destination-ipv4-address>2.2.2.2</destination-ipv4-address></ipv4></endpoints-obj><ero><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24003</sid><local-ip-address>13.0.0.1</local-ip-address><remote-ip-address>13.0.0.3</remote-ip-address></subobject><subobject><loose>false</loose><sid-type>ipv4-adjacency</sid-type><m-flag>true</m-flag><sid>24002</sid><local-ip-address>23.0.0.3</local-ip-address><remote-ip-address>23.0.0.2</remote-ip-address></subobject></ero></arguments><network-topology>pcep-topology</network-topology>");
	                		
	                		//Get tunnelNumber of certain LSP by @lspName
	        				getTunnelNumber(odlController);
	        				//add traffic steering static routes
	        				addTrafficSteer(a9k);
	                		break;
	                	}
	                }
	            
	                a9k.container("config").container("cisco-ios-xr", "interface").list("TenGigE").elem("0/0/2/0").container("service-policy").container("type").container("pbr").leaf("input").set("E-PBR");
					  
			}
			 //northbound update and topology change events
			if(ServiceOperationType.UPDATE == operation && isNotified.booleanValue() == true){
				System.out.println("####### something need to be taked care in Service");
				
				//after handled events, set mark back to false
				//context.getRootNode().container("services").list("qos", "qos").elem("c_qos").leaf("isNotified").set(new ConfBool(false));
			}
		}catch(Exception e){
			System.out.print(e);
		}
		
		return opaque;
		
	}
	
	private void addTrafficSteer(NavuContainer device) throws NavuException{
		
		ConfObject o1 = new ConfIPv4Prefix("88.88.88.88/32");
		ConfObject o2 = new ConfBuf("tunnel-te"+tunnelNumber);
		
		ConfKey key = new ConfKey(new ConfObject[]{o1,o2});
		//ConfKey key = new ConfKey(o2);
		
		device.container("config").container("cisco-ios-xr", "router").container("static").container("address-family").container("ipv4").container("unicast").list("routes").create(new String[]{"88.88.88.88/32", "tunnel-te"+tunnelNumber});
		//device.container("config").container("cisco-ios-xr", "router").container("static").container("address-family").container("ipv4").container("unicast").list("routes").create(key);
		device.container("config").container("cisco-ios-xr", "router").container("static").container("address-family").container("ipv4").container("unicast").list("routes").elem(new String[]{"88.88.88.88/32", "tunnel-te"+tunnelNumber}).leaf("net").set("88.88.88.88/32");
		device.container("config").container("cisco-ios-xr", "router").container("static").container("address-family").container("ipv4").container("unicast").list("routes").elem(new String[]{"88.88.88.88/32", "tunnel-te"+tunnelNumber}).leaf("interface").set("tunnel-te"+tunnelNumber);
	}
	
	private void removeTrafficSteer(NavuContainer device) throws NavuException{
		device.container("config").container("cisco-ios-xr", "router").container("static").container("address-family").container("ipv4").container("unicast").list("routes").delete(new String[]{"88.88.88.88/32", "tunnel-te"+tunnelNumber});
		
		//device.container("config").container("cisco-ios-xr", "router").container("static").container("address-family").container("ipv4").container("unicast").list("routes").delete(new String[]{"88.88.88.88/32"});
	}
	
	private int getTunnelNumber(NavuContainer device) throws ConfException{
		ConfXMLParam[] xmlData = device.container("rpc").container("odl", "rpc-get-tunnel-id").action("get-tunnel-id").call("<node>"+odl_node+"</node><name>"+lspName+"</name>");
        String stringData = ConfXMLParam.toXML(xmlData);
        int start = stringData.indexOf("odl\">")+5;
        int end = stringData.indexOf("</odl:tunnel-id>");
        
        System.out.println(stringData.substring(start, end));
        
		tunnelNumber = Integer.valueOf(stringData.substring(start, end));
		
		return tunnelNumber;
		
//		tunnelNumber = 0;
//		return 0;
	}
	
}
