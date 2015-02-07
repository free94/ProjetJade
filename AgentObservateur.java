import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;

public class AgentObservateur extends Agent {
	// Liste information fournisseur
	private ArrayList<InfoAgent> listeInfoFour;
	// Information Transporteur Principal
	private ArrayList<InfoAgent> listeInfoTransPrin;
	// Liste Transporteur Fournisseur
	private ArrayList<InfoAgent> listeInfoTransFour;
	// interface graphic
	private AgentObservateurGUI myGui;
	// nombre de tour
	private int nbTour = 0;

	private AID horloge;

	protected void setup() {
		// Créer la liste
		listeInfoFour = new ArrayList<InfoAgent>();
		listeInfoTransPrin = new ArrayList<InfoAgent>();
		listeInfoTransFour = new ArrayList<InfoAgent>();
		// créer l'interface
		myGui = new AgentObservateurGUI(this);
		myGui.showGui();
		// Récupérer l'agent horloge
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription serviceDesc = new ServiceDescription();
		serviceDesc.setType("Horloge");
		template.addServices(serviceDesc);
		DFAgentDescription[] result;
		try {
			result = DFService.search(this, template);
			this.horloge = result[0].getName();
		} catch (FIPAException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		// ajouter le comportement pour mettre à jour les info tous les 1
		// secondes
		// inscrire l'agent au horloge
		addBehaviour(new Inscription(horloge, "observateur"));
		addBehaviour(new ServiceTour());

	}

	private class ServiceTour extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("msgDebutTour"),
					MessageTemplate.MatchSender(horloge));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				addBehaviour(new MAJ("Fournisseur"));
				addBehaviour(new MAJ("TransporteurPrincipal"));
				addBehaviour(new MAJ("TransporteurFournisseur"));
				addBehaviour(new Reload());
				// Envoyer le message fin de tour
				ACLMessage msgFinDeTour = new ACLMessage(ACLMessage.INFORM);
				msgFinDeTour.setConversationId("msgFinDeTourObs");
				msgFinDeTour.addReceiver(horloge);
				myAgent.send(msgFinDeTour);
			}

		}

	}

	// Mettre à jour l'information
	private class MAJ extends OneShotBehaviour {

		private String type;

		public MAJ(String type) {
			super();
			this.type = type;
		}

		@Override
		public void action() {
			// recherche des fournisseurs dans le DF et mettre à jour la liste
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType(type);
			template.addServices(sd);
			ArrayList<InfoAgent> listeInfo = null;
			if (type.equals("Fournisseur")) {
				listeInfo = listeInfoFour;
			} else if (type.equals("TransporteurPrincipal")) {
				listeInfo = listeInfoTransPrin;
			} else if (type.equals("TransporteurFournisseur")) {
				listeInfo = listeInfoTransFour;
			}
			try {
				listeInfo.clear();
				DFAgentDescription[] result = DFService.search(myAgent,
						template);
				ACLMessage requete = new ACLMessage(ACLMessage.REQUEST);
				for (int i = 0; i < result.length; ++i) {
					requete.addReceiver(result[i].getName());
				}
				requete.setContent("DemandeInfo");
				requete.setConversationId("observateur");
				requete.setReplyWith("requete" + System.currentTimeMillis()); // value
				myAgent.send(requete);
				// Preparer le template pour recevoir la réponse
				MessageTemplate mt = MessageTemplate.and(
						MessageTemplate.MatchConversationId("observateur"),
						MessageTemplate.MatchInReplyTo(requete.getReplyWith()));
				for (int i = 0; i < result.length; i++) {
					ACLMessage reply = blockingReceive(mt);
					if (reply != null) {
						// Reply received
						InfoAgent info;
						try {
							info = (InfoAgent) reply.getContentObject();
							listeInfo.add(info);
						} catch (UnreadableException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					} else {
						System.out
								.println("Erreur Observateur: info null recu");
					}
				}
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}

		}

	}

	// Actualiser affichage
	private class Reload extends OneShotBehaviour {

		@Override
		public void action() {
			nbTour++;
			Object[][] dataFour = new Object[listeInfoFour.size()][6];
			for (int i = 0; i < dataFour.length; i++) {
				dataFour[i][0] = listeInfoFour.get(i).nom;
				dataFour[i][1] = listeInfoFour.get(i).nbClient;
				dataFour[i][2] = listeInfoFour.get(i).CA;
				dataFour[i][3] = listeInfoFour.get(i).benefice;
				dataFour[i][4] = listeInfoFour.get(i).prixVenteOuTransport;
				dataFour[i][5] = listeInfoFour.get(i).prixAchatOuCapaTrans;
			}
			Object[][] dataTransPrin = new Object[listeInfoTransPrin.size()][6];
			for (int i = 0; i < dataTransPrin.length; i++) {
				dataTransPrin[i][0] = listeInfoTransPrin.get(i).nom;
				dataTransPrin[i][1] = listeInfoTransPrin.get(i).nbClient;
				dataTransPrin[i][2] = listeInfoTransPrin.get(i).CA;
				dataTransPrin[i][3] = listeInfoTransPrin.get(i).benefice;
				dataTransPrin[i][4] = listeInfoTransPrin.get(i).prixVenteOuTransport;
			}
			Object[][] dataTransFour = new Object[listeInfoTransFour.size()][6];
			for (int i = 0; i < dataTransFour.length; i++) {
				dataTransFour[i][0] = listeInfoTransFour.get(i).nom;
				dataTransFour[i][1] = listeInfoTransFour.get(i).nbClient;
				dataTransFour[i][2] = listeInfoTransFour.get(i).CA;
				dataTransFour[i][3] = listeInfoTransFour.get(i).benefice;
				dataTransFour[i][4] = listeInfoTransFour.get(i).prixVenteOuTransport;
				dataTransFour[i][5] = listeInfoTransFour.get(i).prixAchatOuCapaTrans;
			}
			myGui.reload(dataFour, dataTransPrin, dataTransFour, nbTour);

		}

	}

}