package org.workcraft.plugins.son.algorithm;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import org.workcraft.dom.Node;
import org.workcraft.plugins.son.SON;
import org.workcraft.plugins.son.connections.SONConnection;
import org.workcraft.plugins.son.connections.SONConnection.Semantics;
import org.workcraft.plugins.son.elements.Block;
import org.workcraft.plugins.son.elements.ChannelPlace;
import org.workcraft.plugins.son.elements.Condition;
import org.workcraft.plugins.son.elements.PlaceNode;
import org.workcraft.plugins.son.elements.Time;
import org.workcraft.plugins.son.elements.TransitionNode;
import org.workcraft.plugins.son.exception.AlternativeStructureException;
import org.workcraft.plugins.son.exception.TimeEstimationException;
import org.workcraft.plugins.son.exception.TimeEstimationValueException;
import org.workcraft.plugins.son.exception.TimeOutOfBoundsException;
import org.workcraft.plugins.son.granularity.HourMins;
import org.workcraft.plugins.son.granularity.TimeGranularity;
import org.workcraft.plugins.son.granularity.YearYear;
import org.workcraft.plugins.son.gui.TimeConsistencyDialog.Granularity;
import org.workcraft.plugins.son.util.Before;
import org.workcraft.plugins.son.util.Interval;
import org.workcraft.plugins.son.util.ScenarioRef;

public class EstimationAlg extends TimeAlg{

	private SON net;
	//interval[0] is first found specified time interval, interval[1] is the accumulated durations
	private Collection<Interval[]> resultTimeAndDuration =new ArrayList<Interval[]>();

	//default duration provided by user
	private final Interval defaultDuration;
	private TimeGranularity granularity = null;
	private ScenarioRef scenario;
	private Before before;
	private boolean isTwoDir;
	private Color color =Color.ORANGE;

	public EstimationAlg(SON net, Interval d, Granularity g, ScenarioRef s, boolean isTwoDir) {
		super(net);
		this.net = net;
		this.defaultDuration = d;
		this.scenario = s;
		this.isTwoDir = isTwoDir;

		if(g == Granularity.YEAR_YEAR){
			granularity = new YearYear();
		}else if (g == Granularity.HOUR_MINS){
			granularity = new HourMins();
		}

		BSONAlg bsonAlg = new BSONAlg(net);
		before  =  bsonAlg.getBeforeList();
	}

	public void twoDirEstimation(Node n) throws AlternativeStructureException, TimeEstimationException, TimeOutOfBoundsException{
		ConsistencyAlg alg = new ConsistencyAlg(net);
		Time node = null;

		if(hasConflict() && scenario == null)
			throw new AlternativeStructureException("select a scenario for "+net.getNodeReference(n) + " first.");

		boolean specifiedDur = alg.hasSpecifiedDur(n, false, scenario);
		boolean specifiedStart = alg.hasSpecifiedStart(n, scenario);
		boolean specifiedEnd = alg.hasSpecifiedEnd(n, scenario);

		if(n instanceof Time)
			node = (Time)n;
		else return;

		//set default duration
		if(!specifiedDur){
			node.setDuration(defaultDuration);
			if(n instanceof PlaceNode){
				((PlaceNode)n).setDurationColor(color);
			}else if(n instanceof Block){
				((Block)n).setDurationColor(color);
			}
		}

		if(!specifiedStart && specifiedEnd){
			Collection<Interval> end = getSpecifiedEndTime(n);
			if(end.size() == 1){
				if(n instanceof Condition){
					Condition c = (Condition)n;
					Interval result = granularity.subtractTD(end.iterator().next(), c.getDuration());

					Collection<SONConnection> cons = net.getInputScenarioPNConnections(c, scenario);
					if(cons.size() == 1){
						cons.iterator().next().setTime(result);
					}else{
						c.setStartTime(result);
					}
				}
			}else{
				throw new RuntimeException("output > 1");
			}

		}else if(specifiedStart && !specifiedEnd){
			Collection<Interval> start = getSpecifiedStartTime(n);
			if(start.size() == 1){
				if(n instanceof Condition){
					Condition c = (Condition)n;
					Interval result = granularity.plusTD(start.iterator().next(), c.getDuration());

					Collection<SONConnection> cons = net.getOutputScenarioPNConnections(c, scenario);
					if(cons.size() == 1){
						cons.iterator().next().setTime(result);
					}else{
						c.setEndTime(result);
					}
				}
			}else{
				throw new RuntimeException("input > 1");
			}

		}else if(!specifiedStart && !specifiedEnd){

		}

	}

	private boolean hasConflict(){
		RelationAlgorithm alg = new RelationAlgorithm(net);
		for(Condition c : net.getConditions()){
			if(alg.hasPostConflictEvents(c))
				return true;
			else if(alg.hasPreConflictEvents(c))
				return true;
		}
		return false;
	}

	private Collection<Interval> getSpecifiedEndTime(Node n){
		Collection<Interval> result = new ArrayList<Interval>();

		if(n instanceof Condition){
			Condition c = (Condition)n;
			Collection<SONConnection> cons = net.getOutputPNConnections(c);
			Collection<SONConnection> cons2 = net.getOutputScenarioPNConnections(c, scenario);

			//c is final
			if(cons.isEmpty() && cons2.isEmpty()){
				result.add(c.getEndTime());
			//c is final state of the scenario, but not the final state of ON
			}else if(!cons.isEmpty() && cons2.isEmpty()){
				if(cons.size() == 1){
					SONConnection con = cons.iterator().next();
					result.add(con.getTime());
				}
			//c is not final state
			}else if(!cons.isEmpty() && !cons2.isEmpty()){
				SONConnection con = null;
				if(cons2.size()==1)
					con = cons2.iterator().next();
				if(con==null) throw new RuntimeException("output connection != 1" + net.getNodeReference(n));
				result.add(con.getTime());
			}
		}
		return result;
	}

	private Collection<Interval> getSpecifiedStartTime(Node n){
		Collection<Interval> result = new ArrayList<Interval>();

		if(n instanceof Condition){
			Condition c = (Condition)n;
			Collection<SONConnection> cons = net.getInputScenarioPNConnections(c, scenario);
			//p is initial
			if(cons.isEmpty()){
				result.add(c.getStartTime());
			}else{
				SONConnection con = null;
				if(cons.size()==1)
					con = cons.iterator().next();
				if(con==null) throw new RuntimeException(
						"input connection != 1" + net.getNodeReference(n));
				//set estimated start time
				result.add(con.getTime());
			}
		}
		return result;
	}

	public void setEstimatedEndTime(Node n) throws TimeOutOfBoundsException, TimeEstimationException, TimeEstimationValueException{
		Interval end = null;
		clearSets();

		if(n instanceof Condition){
			Condition c = (Condition)n;
			Collection<SONConnection> cons = net.getOutputPNConnections(c);
			Collection<SONConnection> cons2 = net.getOutputScenarioPNConnections(c, scenario);
			//c is final
			if(cons.isEmpty() && cons2.isEmpty()){
				end = getEstimatedEndTime(n);
				//set estimated start time
				if(end != null){
					c.setEndTime(end);
					throw new TimeEstimationValueException("Estimated end time = "+ end.toString() +", from "+intervals(resultTimeAndDuration));
				}
			//c is final state of the scenario, but not the final state of ON
			}else if(!cons.isEmpty() && cons2.isEmpty()){
					if(cons.size() == 1){
						SONConnection con = cons.iterator().next();
						c.setEndTime(con.getTime());
						throw new TimeEstimationValueException("End time = "+ con.getTime().toString());
					}else{
						throw new TimeEstimationException(
						"node has more than one possible end times (forward)");
					}
			//c is not final state
			}else if(!cons.isEmpty() && !cons2.isEmpty()){
				SONConnection con = null;
				if(cons2.size()==1)
					con = cons2.iterator().next();
				if(con==null) throw new RuntimeException("output connection != 1" + net.getNodeReference(n));
				//set estimated value
				if(!con.getTime().isSpecified()){
					end = getEstimatedEndTime(n);
					if(end != null){
						c.setEndTime(end);
						throw new TimeEstimationValueException("Estimated end time = "+ end.toString() +", from "+intervals(resultTimeAndDuration));
					}
				//set value from output connection
				}else{
					c.setEndTime(con.getTime());
					throw new TimeEstimationValueException("End time = "+ con.getTime().toString());
				}
			}
		}else if(n instanceof TransitionNode){
			TransitionNode t = (TransitionNode)n;
			Collection<SONConnection> cons2 = net.getOutputScenarioPNConnections(t, scenario);

			Collection<SONConnection> specifiedConnections = new ArrayList<SONConnection>();
			for(SONConnection con : cons2)
				if(con.getTime().isSpecified())
					specifiedConnections.add(con);

			//some or all of the connections have specified values
			if(!specifiedConnections.isEmpty()){
				if(concurConsistency(specifiedConnections)){
					end =  specifiedConnections.iterator().next().getTime();
					if(end != null){
						t.setEndTime(end);
						throw new TimeEstimationValueException("End time = "+ end.toString());
					}
				}else{
					throw new TimeEstimationException(
							"node is concurrently inconsistency (forward)");
				}
			//none of the connections has specified value.
			}else{
				end = getEstimatedEndTime(n);
				if(end != null){
					t.setEndTime(end);
					throw new TimeEstimationValueException("Estimated end time = "+ end.toString() +", from "+intervals(resultTimeAndDuration));
				}
			}
		}else if(n instanceof ChannelPlace){
			ChannelPlace cp = (ChannelPlace)n;
			end = getEstimatedEndTime(n);
			if(end != null){
				cp.setEndTime(end);
				throw new TimeEstimationValueException("Estimated end time = "+ end.toString() +", from "+intervals(resultTimeAndDuration));
			}
		}
	}

	public void setEstimatedStartTime(Node n) throws TimeOutOfBoundsException, TimeEstimationException, TimeEstimationValueException{
		Interval start = null;
		clearSets();

		if(n instanceof Condition){
			Condition c = (Condition)n;
			Collection<SONConnection> cons = net.getInputScenarioPNConnections(c, scenario);
			//p is initial
			if(cons.isEmpty()){
				start = getEstimatedStartTime(n);
				//set estimated start time
				if(start != null){
					c.setStartTime(start);
					throw new TimeEstimationValueException("Estimated start time = "+ start.toString() +", from "+intervals(resultTimeAndDuration));
				}
			}else{
				SONConnection con = null;
				if(cons.size()==1)
					con = cons.iterator().next();
				if(con==null) throw new RuntimeException(
						"input connection != 1" + net.getNodeReference(n));
				//set estimated start time
				if(!con.getTime().isSpecified()){
					start = getEstimatedStartTime(n);
					if(start != null){
						c.setStartTime(start);
						throw new TimeEstimationValueException("Estimated start time = "+ start.toString() +", from "+intervals(resultTimeAndDuration));
					}
				//set start time from connection
				}else{
					c.setStartTime(con.getTime());
					throw new TimeEstimationValueException("Start time = "+ con.getTime().toString());
				}
			}
		}else if(n instanceof TransitionNode){
			TransitionNode t = (TransitionNode)n;
			Collection<SONConnection> cons2 = net.getInputScenarioPNConnections(t, scenario);

			Collection<SONConnection> specifiedConnections = new ArrayList<SONConnection>();
			for(SONConnection con : cons2)
				if(con.getTime().isSpecified())
					specifiedConnections.add(con);

			//some or all of the connections have specified values
			if(!specifiedConnections.isEmpty()){
				if(concurConsistency(specifiedConnections)){
					start =  specifiedConnections.iterator().next().getTime();
					if(start != null){
						t.setStartTime(start);
						throw new TimeEstimationValueException("Start time = "+ start.toString());
					}
				}else{
					throw new TimeEstimationException(
							"node is concurrently inconsistency (backward)");
				}
			//none of the connections has specified value.
			}else{
				start = getEstimatedStartTime(n);
				if(start != null){
					t.setStartTime(start);
					throw new TimeEstimationValueException("Estimated start time = "+ start.toString() +", from "+intervals(resultTimeAndDuration));
				}
			}
		}else if(n instanceof ChannelPlace){
			ChannelPlace cp = (ChannelPlace)n;
			start = getEstimatedStartTime(n);
			if(start != null){
				cp.setStartTime(start);
				throw new TimeEstimationValueException("Estimated start time = "+ start.toString() +", from "+intervals(resultTimeAndDuration));
			}
		}
	}

	private boolean concurConsistency(Collection<SONConnection> cons){
		SONConnection con = cons.iterator().next();
		Interval time = con.getTime();
		for(SONConnection con1 : cons){
			Interval time1 = con1.getTime();
			if(!time.equals(time1)){
				return false;
			}
		}
		return true;
	}

	private Interval getEstimatedStartTime(Node n) throws TimeEstimationException, TimeOutOfBoundsException{
		Interval result = new Interval();

    	LinkedList<Time> visited = new LinkedList<Time>();
    	visited.add((Time)n);

    	if(scenario != null)
    		backwardDFS(visited, scenario.getNodes(net));

		Collection<Interval> possibleTimes = new ArrayList<Interval>();
		for(Interval[] interval : resultTimeAndDuration){
			possibleTimes.add(granularity.plusTD(interval[0], interval[1]));
    	}
		if(!possibleTimes.isEmpty())
			result = Interval.getOverlapping(possibleTimes);

		if(result != null){
			if(!result.isSpecified()){
				throw new TimeEstimationException(
						"cannot find causally time value (backward).");
			}else
				return result;
		}else
			throw new TimeEstimationException("intervals"+
		intervals(resultTimeAndDuration)+"are not consistent (backward).");
	}

	private Interval getEstimatedEndTime(Node n) throws TimeEstimationException, TimeOutOfBoundsException{
		Interval result = new Interval();

    	LinkedList<Time> visited = new LinkedList<Time>();
    	visited.add((Time)n);

    	if(scenario != null)
    		forwardDFS(visited, scenario.getNodes(net));

		Collection<Interval> possibleTimes = new ArrayList<Interval>();

		for(Interval[] interval : resultTimeAndDuration){
			possibleTimes.add(granularity.subtractTD(interval[0], interval[1]));
    	}

		if(!possibleTimes.isEmpty())
			result = Interval.getOverlapping(possibleTimes);

		if(result != null){
			if(!result.isSpecified()){
				throw new TimeEstimationException(
						"cannot find causally time value (forward).");
			}else
				return result;
		}else
			throw new TimeEstimationException(
					"intervals"+
			intervals(resultTimeAndDuration)+"are not consistent (forward).");

	}

    private void forwardDFS(LinkedList<Time> visited, Collection<Node> nodes)  {
        LinkedList<Time> neighbours = getCausalPostset(visited.getLast(), nodes);

        if (visited.getLast().getEndTime().isSpecified()) {
        	Interval[] result = new Interval[2];
        	result[0] = visited.getLast().getEndTime();
        	result[1] = durationAccumulator1(visited);
            resultTimeAndDuration.add(result);
        }

        // examine post nodes
        for (Time node : neighbours) {
        	SONConnection con = net.getSONConnection(visited.getLast(), node);
            if (visited.contains(node)) {
                continue;
            }
            if (visited.getLast().getEndTime().isSpecified()) {
                break;
            }else if (con!=null && con.getTime().isSpecified()) {
            	Interval[] result = new Interval[2];
            	result[0] = con.getTime();
            	result[1] = durationAccumulator1(visited);
                resultTimeAndDuration.add(result);

            }
        }
        // in depth-first, recursion needs to come after visiting post nodes
        for (Time node : neighbours) {
        	SONConnection con = net.getSONConnection(visited.getLast(), node);
            if (visited.contains(node) || visited.getLast().getEndTime().isSpecified()) {
                continue;
            }else if(con!=null && con.getTime().isSpecified())
                continue;
            visited.addLast(node);
            forwardDFS(visited, nodes);
            visited.removeLast();
        }
    }

    private void backwardDFS(LinkedList<Time> visited, Collection<Node> nodes) {
        LinkedList<Time> neighbours = getCausalPreset(visited.getLast(), nodes);

        if (visited.getLast().getStartTime().isSpecified()) {
        	Interval[] result = new Interval[2];
        	result[0] = visited.getLast().getStartTime();
        	result[1] = durationAccumulator1(visited);

            resultTimeAndDuration.add(result);
        }

        // examine post nodes
        for (Time node : neighbours) {
        	SONConnection con = net.getSONConnection(node, visited.getLast());
            if (visited.contains(node)) {
                continue;
            }
            if (visited.getLast().getStartTime().isSpecified()) {
                break;
            }else if (con!=null && con.getTime().isSpecified()) {
            	Interval[] result = new Interval[2];
            	result[0] = con.getTime();
            	result[1] = durationAccumulator1(visited);
                resultTimeAndDuration.add(result);
            }
        }
        // in depth-first, recursion needs to come after visiting post nodes
        for (Time node : neighbours) {
        	SONConnection con = net.getSONConnection(node, visited.getLast());
            if (visited.contains(node) || visited.getLast().getStartTime().isSpecified()) {
                continue;
            }else if(con!=null && con.getTime().isSpecified()){
                continue;
            }
            visited.addLast(node);
            backwardDFS(visited, nodes);
            visited.removeLast();

        }
    }

    private Interval durationAccumulator1 (LinkedList<Time> visited){
    	Interval result = new Interval(0000, 0000);
    	Time first = visited.getFirst();
    	for(Time time : visited){
    		if(time != first){
	    		if (time.getDuration().isSpecified())
	    			result = result.add(time.getDuration());
	    		else{
	    			result = result.add(defaultDuration);
	    		}
    		}
    	}
    	return result;
    }

//    private Interval durationAccumulator2 (LinkedList<Time> visited){
//    	Interval result = new Interval(0000, 0000);
//    	Time first = visited.getFirst();
//    	Time last =  visited.getLast();
//    	for(Time time : visited){
//    		if(time != first || time != last){
//	    		if (time.getDuration().isSpecified())
//	    			result = result.add(time.getDuration());
//	    		else{
//	    			result = result.add(defaultDuration);
//	    		}
//    		}
//    	}
//    	return result;
//    }

    private LinkedList<Time> getCausalPreset(Time n, Collection<Node> nodes){
    	LinkedList<Time> preSet = new LinkedList<Time>();
    	LinkedList<Time> result = new LinkedList<Time>();

    	for(TransitionNode[] pre : before){
    		if(pre[1] == n)
    			preSet.add(pre[0]);
    	}

    	for(Node node : getPrePNSet(n)){
    		if(node instanceof Time)
    			preSet.add((Time)node);
    	}
    	if(isInitial(n) && (n instanceof Condition)){
    		preSet.addAll(getPostBhvSet((Condition)n));
    	}else if(n instanceof TransitionNode){
    		for(SONConnection con : net.getSONConnections(n)){
    			if(con.getSemantics() == Semantics.SYNCLINE){
    				if(con.getFirst() == n)
    					preSet.add((Time)con.getSecond());
    				else
    					preSet.add((Time)con.getFirst());
    			}else if(con.getSemantics() == Semantics.ASYNLINE && con.getSecond() == n)
  					preSet.add((Time)con.getFirst());
    		}
    	}else if(n instanceof ChannelPlace){
    		Node input = net.getPreset(n).iterator().next();
    		preSet.add((Time)input);
    		Collection<Semantics> semantics = net.getSONConnectionTypes(n);
    		if(semantics.iterator().next() == Semantics.SYNCLINE){
        		Node output = net.getPostset(n).iterator().next();
        		preSet.add((Time)output);
    		}
    	}

    	for(Time node : preSet){
    		if(nodes.contains(node))
    			result.add(node);
    	}

    	return result;
    }

    private LinkedList<Time> getCausalPostset(Time n, Collection<Node> nodes){
    	LinkedList<Time> postSet = new LinkedList<Time>();
    	LinkedList<Time> result = new LinkedList<Time>();

    	for(TransitionNode[] post : before){
    		if(post[0] == n)
    			postSet.add(post[1]);
    	}

    	for(Node node :getPostPNSet(n)){
    		if(node instanceof Time)
    			postSet.add((Time)node);
    	}

    	if(isFinal(n) && (n instanceof Condition)){
    		postSet.addAll(getPostBhvSet((Condition)n));
    	}else if(n instanceof TransitionNode){
    		for(SONConnection con : net.getSONConnections(n)){
    			if(con.getSemantics() == Semantics.SYNCLINE){
    				if(con.getFirst() == n)
    					postSet.add((Time)con.getSecond());
    				else
    					postSet.add((Time)con.getFirst());
    			}else if(con.getSemantics() == Semantics.ASYNLINE && con.getFirst() == n)
    				postSet.add((Time)con.getSecond());
    		}

    	}else if(n instanceof ChannelPlace){
    		Node output = net.getPostset(n).iterator().next();
    		postSet.add((Time)output);
    		Collection<Semantics> semantics = net.getSONConnectionTypes(n);
    		if(semantics.iterator().next() == Semantics.SYNCLINE){
        		Node input = net.getPreset(n).iterator().next();
        		postSet.add((Time)input);
    		}
    	}

    	for(Time node : postSet){
    		if(nodes.contains(node))
    			result.add(node);
    	}

    	return result;
    }

	public String intervals(Collection<Interval[]> intervals){
		ArrayList<String> strs = new ArrayList<String>();
		for(Interval[] interval : intervals){
			strs.add(interval[0].toString());
		}
		return "("+strs.toString()+")";
	}

	public Collection<Interval[]> getResultTimeAndDuration() {
		return resultTimeAndDuration;
	}

	public void clearSets(){
		resultTimeAndDuration.clear();
	}
}
