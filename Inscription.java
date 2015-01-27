import jade.core.AID;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;

public class Inscription extends OneShotBehaviour {
	private AID horloge = null;
	private String type = "";

	public Inscription(AID horloge, String type) {
		super();
		this.horloge = horloge;
		this.type = type;

	}

	@Override
	public void action() {
		ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
		msg.addReceiver(this.horloge);
		msg.setConversationId("inscriptionHorloge");
		msg.setContent(type);
		myAgent.send(msg);

	}

}