package mcmo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.PriorityQueue;

/**
 * This class implements the generalized cost-benefit greedy algorithm (Zhang 2016)
 * @author zhangh24
 * @param <V>
 *
 */
public class Algorithm4<V> {
	private static final boolean USE_CHRISTOFIDES=true;
	
	public Algorithm4() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Single source Dijkstra algorithm
	 * @param graph graph
	 * @param source source vertex
	 * @return a list of distance from source and preceding vertex for each vertex
	 */
	public static <V> WrappedObject <V> dijkstraShortestPath (Graph<V> graph,V source) {
		/*System.out.println("Shortest Path:"+source+"->ALL");*/

		HashMap<V, Double> dist = new HashMap<V, Double>(); //distance to all vertices
		HashMap<V, V> prev = new HashMap<V, V>(); //predecessor list

		PriorityQueue<Vertex<V>> Q = new PriorityQueue<>();


		graph.getVertexList().get(source).min_dist=0;
		dist.put(source, 0.0);

		graph.getVertexList().get(source).pre_node=null;
		prev.put(source, null);

		//Initialization
		for (V v: graph.getVertexList().keySet()){
			if (!v.equals(source)) {
				graph.getVertexList().get(v).min_dist=Double.POSITIVE_INFINITY;
				dist.put(v, Double.POSITIVE_INFINITY);				

				//prev.put(v, null);
				graph.getVertexList().get(v).pre_node=null;
				prev.put(v, null);

			}
			Q.offer(graph.getVertexList().get(v));			
		}	

		//Main Procedure
		while (!Q.isEmpty()){
			Vertex u=Q.poll();

			for (V v : graph.getAdjacentVertices((V) u.getID())){
				//double alt = dist.get(u) + graph.getDistanceBetween(u, v);
				double alt = u.min_dist + graph.getDistanceBetween((V) u.getID(), v);
				if (alt<graph.getVertexList().get(v).min_dist){
					Q.remove(graph.getVertexList().get(v));	

					graph.getVertexList().get(v).min_dist=alt;
					dist.put(v, alt);											

					graph.getVertexList().get(v).pre_node=u.getID();
					prev.put(v, (V) u.getID());

					Q.add(graph.getVertexList().get(v));					
				}				
			}
		}

		/*System.out.println("Dist:"+dist);*/
		return new WrappedObject(dist, prev);
	}
	/**
	 * Compute optimal walk using cost-benefit heuristic
	 * @param road_network routing network incorporating houses and way points 
	 * @param social_network social diffusion network
	 * @param start start vertex, i.e., a way point
	 * @param target end vertex, i.e., a way point
	 * @param budget budget
	 * @param iniActiveNodes initially active vertex, i.e., adopters
	 * @return a walk as sequence of vertices in road_network
	 * @throws Exception 
	 */
	public static <V> ArrayList <V> greedyWalk(int mode, Graph<V> road_network, InfluenceModel<V> social_network, 
			V start, V target, double budget, HashSet <V> iniActiveNodes) throws Exception {
		//DEFINED two lists: one for visited set, one for unvisited set
		HashSet<V> visited_set=new HashSet<V>();
		HashSet<V> unvisited_set=new HashSet<V>();

		//ADD all social nodes to unvisited set if not initially visited
		for (V v:social_network.getSocialNetWork().getVertexList().keySet()){
			if(iniActiveNodes.contains(v)) visited_set.add(v);
			else unvisited_set.add(v);
		}

		//BEST walk tracked so far
		ArrayList <V> walk_best = new ArrayList <V>();
		double infl_cur=social_network.getExpectedInfluence(visited_set); //influence before choosing a new vertex

		//PICK the node in the unvisited set with max marginal influence per cost and ADD to the visited set
		while (!unvisited_set.isEmpty()){
			//System.out.println("#of candidate:"+unvisited_set.size());
			V node_best_iter = null;  //BEST node for this iteration
			ArrayList <V> walk_best_iter = new ArrayList <V>(); //BEST walk for this iteration

			double fc_max_iter=0; //MAX influence for this iteration
			//double delta_f_max=0; //MAX influence change for this iteration
			double cost_best_iter=0; //BEST cost by adding BEST node

			//double infl_cur=social_network.getExpectedInfluence(visited_set); //influence before choosing a new vertex			
						
			double cost_cur=0;
			//System.out.println(USE_CHRISTOFIDES);
			if(USE_CHRISTOFIDES) 
				cost_cur=Graph.shortestCoverCostChristofides(start,road_network,exclude(visited_set, iniActiveNodes)).length;//[aij]
			else 
				cost_cur=Graph.shortestCoverCostGreedy(start,road_network,exclude(visited_set, iniActiveNodes)).length;//[aaai16]
			
			//ArrayList <V> walk_cur=Graph.shortestCoverCostChristofides(start,road_network,exclude(visited_set, iniActiveNodes)).walk;

			//System.out.println("visited_set:"+visited_set);
			//System.out.println("walk_cur:"+walk_cur);		


			if(mode==1) cost_cur+=ChannelDoorToDoor.visit_cost*exclude(visited_set, iniActiveNodes).size();//cost before choosing a new vertex, i.e., approximate shortest walk covering all nodes
			else if(mode==2) cost_cur+=Driver_rg.visit_cost*exclude(visited_set, iniActiveNodes).size();

			for(V v:unvisited_set){	
				//System.out.println("Adding:"+v);
				HashSet <V> u=union(v, visited_set); //Union with visited set

				//Walk and cost
				WrapWalkWithLength walk_new;
				
				if(USE_CHRISTOFIDES)
					walk_new=Graph.shortestCoverCostChristofides(start,road_network,exclude(u,iniActiveNodes)); //[aij]
				else
					walk_new=Graph.shortestCoverCostGreedy(start,road_network,exclude(u,iniActiveNodes));//[aaai16]
				//System.out.println("walk_new:"+walk_new.walk);

				double cost_new=walk_new.length;
				if (mode==1) cost_new+=ChannelDoorToDoor.visit_cost*exclude(u,iniActiveNodes).size(); //cost of adding the new vertex, i.e., approximate shortest walk covering all nodes
				else if(mode==2) cost_new+=Driver_rg.visit_cost*exclude(u,iniActiveNodes).size(); 

				//System.out.println("cost_new:"+cost_new);								

				//OPTIMIZE PERFORMANCE: method 1
				//SKIP infeasible nodes to boost speed
				//Twice fast
				if(ChannelDoorToDoor.prune_mode||Driver_rg.prune_mode){
					if(cost_new>budget) {
						//System.out.println("Infeasible!");
						continue;						
					}
				}				

				double delta_cost=cost_new-cost_cur; //cost changes
				//System.out.println("delta_c="+delta_cost);

				//Influence								
				double infl_new=social_network.getExpectedInfluence(u); //influence when adding the new vertex
				double delta_f=infl_new-infl_cur; //influence changes

				//System.out.println("delta_f="+delta_f);

				double fc=delta_f/delta_cost;//gradient

				//Special case: adding one extra node won't change the cover cost, i.e., visit cost=0. 
				//				if(delta_cost==0&&delta_f>0){
				//					System.out.println("Free addition!");
				//					System.out.println("visited_set:"+visited_set);
				//					System.out.println("walk_cur:"+walk_cur);
				//					System.out.println("v|u:"+v+"|"+u);
				//					System.out.println("walk_new:"+walk_new.walk);
				//					System.out.println("delta_c="+delta_cost);
				//					Graph.shortestCoverCostChristofides(start,road_network,exclude(u,iniActiveNodes));
				//					node_best_iter=v;
				//					walk_best_iter=walk_new.walk;
				//					cost_best_iter=cost_new;
				//					break;//stop checking all others candidates					
				//				}

				if (fc>=fc_max_iter){
					//					System.out.println("visited_set:"+visited_set);
					//					System.out.println("walk_cur:"+walk_cur);
					//					System.out.println("Good Candy!");
					//					System.out.println("v|u:"+v+"|"+u);
					//					System.out.println("walk_new:"+walk_new.walk);
					//					System.out.println("delta_c="+delta_cost);
					//					System.out.println("delta_f="+delta_f);
					//					System.out.println(v+":"+fc);

					infl_cur=infl_new;//jair
					node_best_iter=v;
					fc_max_iter=fc;					
					//delta_f_max=delta_f;					
					walk_best_iter=walk_new.walk;
					cost_best_iter=cost_new;									
				}else{
					//System.out.println("Feasible, but Bad Candy!");
				}
			}			

			if(node_best_iter!=null&&cost_best_iter<=budget){ //method 0: 
				//if (node_best_iter!=null){  //method 1 
				walk_best=walk_best_iter;
				visited_set.add(node_best_iter); //ADD best node to visited set
				unvisited_set.remove(node_best_iter); //REMOVE best node from unvisited set
				//System.out.println("visited set:"+visited_set+";cost:"+cost_new_max+";walk:"+walk_new_max);
				if(mode==1){
					//Driver.max_influe+=delta_f_max; //Track influence changes
					ChannelDoorToDoor.budget=cost_best_iter;//Track budget usage
					ChannelDoorToDoor.visit_set=exclude(visited_set, iniActiveNodes);
				}else if(mode==2){
					//Driver_rg.max_influe+=delta_f_max; //Track influence changes
					Driver_rg.budget=cost_best_iter;//Track budget usage
					Driver_rg.visit_set=exclude(visited_set, iniActiveNodes);
				}


			}else {
				//System.out.println("Last Node:"+node_best_iter);
				//System.out.println("Last Cost:"+cost_best_iter);				
				break;
			}
		}


		//Adjusted Influence[jair]
		double influAdj=(visited_set.size()==iniActiveNodes.size())?0:
			social_network.getExpectedInfluence(visited_set)-social_network.getExpectedInfluence(iniActiveNodes);
		
		//System.out.println();
		//
		if(influAdj<0){
			//System.out.println(influAdj);
			//System.out.println();			
		}
		
		if(mode==1) ChannelDoorToDoor.max_influe=influAdj;
		else if(mode==2){
			Driver_rg.max_influe=influAdj;
		}

		return walk_best;		
	}


	public static <V> HashSet union(V new_node, HashSet <V> active_nodes){
		HashSet <V> union=new HashSet <V> (active_nodes);		
		union.add(new_node);
		return union;		
	}

	public static <V> HashSet union( HashSet <V> node_set_1, HashSet <V> node_set_2){
		HashSet <V> union=new HashSet <V> (node_set_1);
		//		for (V v: node_set_2){
		//			union.add(v);		
		//		}
		union.addAll(node_set_2);
		return union;			
	}	

	public static <V> HashSet<V> exclude( HashSet <V> node_set_1, HashSet <V> node_set_2){
		HashSet <V> exclude=new HashSet <V> ();
		for(V v: node_set_1){
			if(!node_set_2.contains(v)) exclude.add(v);  
		}		
		return exclude;			
	}
}
