package edu.pku.dblab.binary;

//2014-11-22 Lei Gai. Framework for testing edge based graph pattern index.

import java.io.*;
import java.util.*;
//import java.nio.*;

public class IGraph2BinaryIndex {

	private static final String EPI_FILE = "D:\\data\\graphdb.epi";
	private static final String ELI_FILE = "D:\\data\\graphdb.eli";
	private static final String IGRAPH_FILE = "D:\\data\\yeast.igraph";
//	private static final String OUTPUT_FILE = "D:\\construction_log.txt";
	
//	private static final int MAX_VERTICES = 30000;
	private static final int PATH_IN_BLOCK = 255;
	private static final int BLOCK_SIZE = (PATH_IN_BLOCK+1)*8;
	private static final int MARGIN = 10*20;

	public static void main(String[] args){
		try {
			
			//Record Start time of each query.
			long startTime = System.currentTimeMillis();

			//Create binary index file
			File epiFile = new File(EPI_FILE);
			if (epiFile.exists()){
				epiFile.delete();
	          }  
			File eliFile = new File(ELI_FILE);
			if (eliFile.exists()){
				eliFile.delete();
	          }  
			
			RandomAccessFile edgePatternIndex = new RandomAccessFile(epiFile, "rw");
			RandomAccessFile edgeListIndex = new RandomAccessFile(eliFile, "rw");
			
			//log the final result.
//			File outputFile = new File(OUTPUT_FILE);
//			if (outputFile.exists()){
//				outputFile.delete();
//	          }  
//			String crlf = System.getProperty("line.separator");
//			OutputStream os = new FileOutputStream(outputFile);
//			os.write("Checking...".getBytes());
//			os.write(crlf.getBytes());
//			String log = "";

			//Reading from iGraph file
			FileReader igraphFile = new FileReader(IGRAPH_FILE);
			BufferedReader br = new BufferedReader(igraphFile);

			//If possible, hold all node-label information in main memory.
			Map<Integer,List<Integer>> nodeLabel = new TreeMap<Integer,List<Integer>>();
			//If possible, hold all pattern header information in main memory.
			//combination of two TreeMap.
			Map<Integer,Map<Integer,long[]>> patternHeader = new TreeMap<Integer,Map<Integer,long[]>>();
			String str = br.readLine();
			
			int num_vertex = 0;
			int num_pattern = 0;
			int num_label = 0;
			boolean init_file = false;
			
			while (str != null) {
				String[] words = str.split(" ");
				switch(words[0]){
				case "v":
					//System.out.println("words[1]="+words[1]);
					Integer nodeID = Integer.valueOf(words[1]);
					List<Integer> labelList = new ArrayList<Integer>();
					for(int i=2;i<words.length;i++){
						if( Integer.valueOf(words[i]).intValue() == 0){
//							log = "No Label: node "+ nodeID.toString() ;
//							os.write(log.getBytes());
//							os.write(crlf.getBytes());
							labelList = new ArrayList<Integer>();
							labelList.add(Integer.valueOf(0));
							break;
						}else{
							labelList.add(Integer.valueOf(words[i]));
							if(Integer.valueOf(words[i])> num_label){
								num_label = Integer.valueOf(words[i]);
							}
						}
					}
					//System.out.println("Get node: "+nodeID.intValue());
					nodeLabel.put(nodeID, labelList);
					num_vertex++;
					break;
				case "e":
					if(!init_file){
						//First time, allocate free space for file header.
						init_file = true;
						byte[] buffer = new byte[4 + 8+ 20*(num_label*(num_label-1)/2)+ MARGIN];
						edgePatternIndex.write(buffer);
						
//						int pattern_offset = 4 + 8+ 20*(num_label*(num_label-1)/2)+ MARGIN + num_pattern*BLOCK_SIZE;
//						log = "Init index file with buffer size "+pattern_offset;
//						os.write(log.getBytes());
//						os.write(crlf.getBytes());
//						System.out.println(log);

					}
					
//					log = "Starting of another line in file=================================================";
//					os.write(log.getBytes());
//					os.write(crlf.getBytes());
//					System.out.println(log);
//
//					String log = "Get edge: n"+words[1]+"->n"+words[2];
//					os.write(log.getBytes());
//					os.write(crlf.getBytes());
//					System.out.println(log);
					
					Integer sourceNode = Integer.valueOf(words[1]);
					Integer destNode = Integer.valueOf(words[2]);
					
					List<Integer> sourceLabelList = nodeLabel.get(sourceNode);
					List<Integer> destLabelList = nodeLabel.get(destNode);
					
					for(Integer sourceLabel:sourceLabelList){
						
						int curLabela = sourceLabel.intValue();
						if (curLabela == 0) break;
						
						for(Integer destLabel:destLabelList){
							//get current incoming label pattern(pair)
							int curLabelb = destLabel.intValue();
							if(curLabelb == 0) break;
							
//							log = "Current Label::"+curLabela+"-"+curLabelb+",Node pair:"+sourceNode.toString()+"-"+destNode.toString()+" ||"+edgePatternIndex.getFilePointer()+"||"+edgePatternIndex.length();
//							os.write(log.getBytes());
//							os.write(crlf.getBytes());
//							System.out.println(log);

							//Search in-memory structure for pattern matching.
							boolean hasPattern = false;
							Integer matched_label = null;
							Integer other_label = null;
	
							if(patternHeader.containsKey(sourceLabel)){
								if(patternHeader.get(sourceLabel).containsKey(destLabel)){
									
									hasPattern = true;
									
									//write edgeID to index file, and update in-memory info
									long[] params = patternHeader.get(sourceLabel).get(destLabel);
									int num_edge = (int)params[0];
									long offset = params[1];
										
										//edgelist is stored as 31 edgeID(4+4bytes) and a long offset.
									while(num_edge >= PATH_IN_BLOCK){
										edgePatternIndex.seek(offset + PATH_IN_BLOCK*8);
										num_edge = num_edge -PATH_IN_BLOCK;
										
										if(num_edge == 0){
											long end_of_file = edgePatternIndex.length();
											edgePatternIndex.writeLong(end_of_file);
											
											//skip to the end of the file
											edgePatternIndex.seek(end_of_file);
											edgePatternIndex.write(new byte[BLOCK_SIZE]);
											
											offset = end_of_file;
										}else{
											offset = edgePatternIndex.readLong();
										}
									
									}

									offset = offset + num_edge*8;
									//write edgeID to existing block at given offset.
									edgePatternIndex.seek(offset);
									edgePatternIndex.writeInt(sourceNode.intValue());
									edgePatternIndex.writeInt(destNode.intValue());
									params[0] = params[0]+1;
									
//									log = "Matched:"+sourceLabel.toString()+"-"+destLabel.toString()+",Node pair:"+sourceNode.toString()+"-"+destNode.toString()+" ||"+edgePatternIndex.getFilePointer()+"||"+edgePatternIndex.length();
//									os.write(log.getBytes());
//									os.write(crlf.getBytes());
//									System.out.println(log);
								}else{
									hasPattern = false;
									matched_label = sourceLabel;
									other_label = destLabel;
								}
							}
							
							if((!hasPattern) && (patternHeader.containsKey(destLabel))){
								if(patternHeader.get(destLabel).containsKey(sourceLabel)){
									hasPattern = true;
									//write edgeID to index file, and update in-memory info
									long[] params = patternHeader.get(destLabel).get(sourceLabel);
									int num_edge = (int)params[0];
									long offset = params[1];

									//edgelist is stored as 31 edgeID(4+4bytes) and a long offset.
									while(num_edge >= PATH_IN_BLOCK){
										edgePatternIndex.seek(offset + PATH_IN_BLOCK*8);
										num_edge = num_edge -PATH_IN_BLOCK;
										
										if(num_edge == 0){
											long end_of_file = edgePatternIndex.length();
											edgePatternIndex.writeLong(end_of_file);
											
											//skip to the end of the file
											edgePatternIndex.seek(end_of_file);
											edgePatternIndex.write(new byte[BLOCK_SIZE]);
											
											offset = end_of_file;
											//break;
										}else{
											//edgePatternIndex.seek(offset);
											offset = edgePatternIndex.readLong();
										}
									
									}

									offset = offset + num_edge*8;
									//write edgeID to existing block at given offset.
									edgePatternIndex.seek(offset);
									
									edgePatternIndex.writeInt(sourceNode.intValue());
									edgePatternIndex.writeInt(destNode.intValue());
									params[0] = params[0]+1;
									
//									log = "Matched:"+sourceLabel.toString()+"-"+destLabel.toString()+",Node pair:"+sourceNode.toString()+"-"+destNode.toString()+" ||"+edgePatternIndex.getFilePointer()+"||"+edgePatternIndex.length();
//									os.write(log.getBytes());
//									os.write(crlf.getBytes());
//									System.out.println(log);

								}else{
									hasPattern = false;
									matched_label = destLabel;
									other_label = sourceLabel;
								}
							}
							
							if(!hasPattern){
								//Such pattern combination never exist.
								//So we need to create new entry for such pair.
								//write edgeID to index file, and update in-memory info
								long end_of_file = edgePatternIndex.length();
								edgePatternIndex.seek(end_of_file);
								edgePatternIndex.writeInt(sourceNode.intValue());
								edgePatternIndex.writeInt(destNode.intValue());
								edgePatternIndex.write(new byte[BLOCK_SIZE-8]);

								long[] params = new long[2];
								params[0]=1;
								params[1]= end_of_file;

								if((matched_label == null) && (other_label == null)){
	
									Map<Integer,long[]> new_entry = new TreeMap<Integer,long[]>();
									new_entry.put(destLabel,params);
									patternHeader.put(sourceLabel, new_entry);
									
//									log = "Not Matched:"+destLabel.toString()+",Not matched:"+sourceLabel.toString()+",Node pair:"+sourceNode.toString()+"-"+destNode.toString()+" ||"+edgePatternIndex.getFilePointer()+"||"+edgePatternIndex.length();
//									os.write(log.getBytes());
//									os.write(crlf.getBytes());
//									System.out.println(log);

								}else{

									patternHeader.get(matched_label).put(other_label, params);
									
//									log = "Partial Matched:"+matched_label.toString()+",Not matched:"+other_label.toString()+",Node pair:"+sourceNode.toString()+"-"+destNode.toString()+" ||"+edgePatternIndex.getFilePointer()+"||"+edgePatternIndex.length();
//									os.write(log.getBytes());
//									os.write(crlf.getBytes());
//									System.out.println(log);
								}
								
								num_pattern++;
//								log = "========size:"+patternHeader.size()+"============";
//								os.write(log.getBytes());
//								os.write(crlf.getBytes());
//								System.out.println(log);
								
							}
//							log = "One round ended ================================||"+edgePatternIndex.getFilePointer()+"||"+edgePatternIndex.length();
//							os.write(log.getBytes());
//							os.write(crlf.getBytes());
//							System.out.println(log);

							
						}
					}
					//num_pattern++;
					break;
					
					
/*							//System.out.println("Edge::v"+sourceNode.intValue()+"-v"+destNode.intValue()+". Current Label:"+sourceLabel.intValue()+"->"+destLabel.intValue()+", pos:"+filePos);
							boolean hasPattern = false;
//							byte[] currentPatternID = concatenateByteArrays(
//									ByteBuffer.allocate(4).putInt(sourceLabel.intValue()).array(),
//									ByteBuffer.allocate(4).putInt(destLabel.intValue()).array());
//							byte[] currentEdgeID = concatenateByteArrays(
//									ByteBuffer.allocate(4).putInt(sourceNode.intValue()).array(),
//									ByteBuffer.allocate(4).putInt(destNode.intValue()).array());
							int curLabela = sourceLabel.intValue();
							int curLabelb = destLabel.intValue();
							
							//Scan the whole file, check whether pattern existed.
							while((!isFirstPattern)&&(filePos<edgePatternIndex.length())){
								//System.out.println("Current file position:"+filePos+",file length:"+edgePatternIndex.length());
								//String s = new String(currentPatternID);
								//System.out.println("Check existing pattern"+s+","+filePos+",pos:"+edgePatternIndex.getFilePointer());
								edgePatternIndex.seek(filePos);
								int existLabela = edgePatternIndex.readInt();
								int existLabelb = edgePatternIndex.readInt();
								
								//System.out.println("Current label:"+curLabela+"/"+curLabelb+". Existing label:"+existLabela+"/"+existLabelb);
								//byte[] existedPatternID = new byte[8];
								//edgePatternIndex.read(existedPatternID); 
								//if(Arrays.equals(existedPatternID, currentPatternID)){
								if(((curLabela == existLabela) && (curLabelb == existLabelb)) || ((curLabela == existLabelb) && (curLabelb == existLabela))){
									//System.out.println("Detected an existing pattern"+existLabela+"/"+existLabelb+",pos:"+edgePatternIndex.getFilePointer());
									int currentOffset = (int)edgePatternIndex.getFilePointer();
									int edgeListOffset = edgePatternIndex.readInt();
									//add new edgeID to pattern edge list.
									edgePatternIndex.writeInt(sourceNode.intValue());
									edgePatternIndex.writeInt(destNode.intValue());
									
									//update offset info.
									edgePatternIndex.seek(currentOffset+8);
									edgePatternIndex.writeInt(edgeListOffset+8);
									edgePatternIndex.skipBytes(8192-12);
									hasPattern = true;
									break;
								}else{
									edgePatternIndex.skipBytes(8192-8);
								}
								//System.out.println("Check existing pattern: "+filePos+",pos:"+edgePatternIndex.getFilePointer());
								filePos=(int)edgePatternIndex.getFilePointer();
								
							}//while
							
							if(!hasPattern){
								//System.out.println("Add new pattern to index"+edgePatternIndex.getFilePointer());
								//add new pattern into index. Assign 8192 bytes.
								edgePatternIndex.skipBytes( (int)edgePatternIndex.length() );
								//System.out.println("length:"+edgePatternIndex.length());
								//edgePatternIndex.write(currentPatternID);
								edgePatternIndex.writeInt(curLabela);
								edgePatternIndex.writeInt(curLabelb);
								
								int currentOffset = (int)edgePatternIndex.getFilePointer();
								edgePatternIndex.writeInt(currentOffset+12);
								//edgePatternIndex.write(currentEdgeID);
								edgePatternIndex.writeInt(sourceNode.intValue());
								edgePatternIndex.writeInt(destNode.intValue());
								
								edgePatternIndex.write(new byte[8172]);
								filePos=(int)edgePatternIndex.getFilePointer();
							}else{
								filePos=(int)edgePatternIndex.getFilePointer();
							}
						}
					}
					isFirstPattern = false;
					break;
*/				
				default:
					break;

				}
				str = br.readLine();
			}
			
			//Reading file DONE. Write content in data structure "patternHeader" to index file "edgePatternIndex".
			edgePatternIndex.seek(0);
			edgePatternIndex.writeInt(num_pattern);
			edgePatternIndex.writeLong(4 + 8 + 20*num_vertex + MARGIN );
			Iterator<Integer> outer_it = patternHeader.keySet().iterator();  
	        while (outer_it.hasNext()) { 
	        	Integer sourceVertex = outer_it.next();
	        	Map<Integer,long[]> inner_key = patternHeader.get(sourceVertex);  
	        	Iterator<Integer> inner_it = inner_key.keySet().iterator();  
	        	while(inner_it.hasNext()){
	        		Integer destVertex = inner_it.next();
	        		long[] params = inner_key.get(destVertex);
	        		edgePatternIndex.writeInt(sourceVertex.intValue());
	        		edgePatternIndex.writeInt(destVertex.intValue());
	        		edgePatternIndex.writeInt((int)params[0]);
	        		edgePatternIndex.writeLong(params[1]);
//	        		String s = "Map:: "+sourceVertex.toString()+"-"+destVertex.toString()+". count:"+params[0]+". offset:"+params[1];
//	    			os.write(s.getBytes());
//	    			os.write(crlf.getBytes());
//	    			System.out.println(s);
     		
	        	}
	        }  

			
			long endTime = System.currentTimeMillis();
			long queryTime = endTime - startTime;

    		String s = "Total running time:"+queryTime;
//			os.write(s.getBytes());
//			os.write(crlf.getBytes());
			System.out.println(s);
		
			//finalize
//	        os.close();
			br.close();
			igraphFile.close();
			edgePatternIndex.close();
			edgeListIndex.close();

			
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}
	
//	private static byte[] concatenateByteArrays(byte[] a, byte[] b) {
//	    byte[] result = new byte[a.length + b.length]; 
//	    System.arraycopy(a, 0, result, 0, a.length); 
//	    System.arraycopy(b, 0, result, a.length, b.length); 
//	    return result;
//	} 
	
}
