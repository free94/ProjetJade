import java.io.IOException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

//agent de transport que tout fournisseur peut créer
public class AgentTransportFournisseur extends Agent {
	
	//nos variables
	private AID createur = null;//AID du créateur du transporteur
	private AID abonne = null;//Un seul fournisseur peut utiliser la ligne de transport créer par un fournisseur
	private int CA = 0;
	private int benefice = 0;
	private int tarif; 	//prix de l'utilisation de la ligne de transport pour un autre fournisseur que le créateur ! Peut être maj
	private boolean disponible = false;//indique si le transporteur est laissé disponible par son créateur pour les autres fournisseurs en début de tour
									   //puis indique simplement si le transporteur peut être pris par un fournisseur ou pas
	private int capaciteTransport = 30;
	protected void setup() {
		// Enregistrement du service dans le DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("TransporteurFournisseur");
		sd.setName(getAID().getLocalName());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		addBehaviour(new ServiceObservateur());
	}
	
	protected void takeDown() {
		// Printout a dismissal message
		System.out.println("Le transporteur fournisseur: "+getAID().getName()+" est terminé.");
	}

	//comportement en cas de demande du tarif de location de la ligne de transport -> réponse favorable uniquement si disponible
	private class demandeTarif extends CyclicBehaviour{		
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("demandeTarif"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
				try {
					msg1.addReceiver(msg.getSender());
					int date = (int) System.currentTimeMillis();
					Devis devis = new Devis(msg.getSender(), myAgent.getAID(), capaciteTransport, date, tarif);
					msg1.setContentObject(devis);
					msg1.setConversationId("tarifTransporteurFournisseur");
					if(disponible == false)
						msg1.setContent("indisponible");
					else
						msg1.setContent("disponible");
					myAgent.send(msg1);
				} catch (IOException e) {
					System.out.println("Erreur génération de facture");
				}								
			}
			else {
				block();
			}
		}		
	}
	
	private class initDisponibilite extends OneShotBehaviour{
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("initDisponibilite"),
					MessageTemplate.MatchSender(createur)
					);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				try {
					disponible = (Boolean) msg.getContentObject();
				} catch (UnreadableException e) {
					e.printStackTrace();
				}
				//Si on est indisponible, sous entendu réservé par et pour le créateur, on lui transmet de suite la facture
				if(!disponible){
					abonne = createur;
					ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
					FactureTransporteur f = new FactureTransporteur(getLocalName(), 0);
					try {
						msg1.addReceiver(createur);
						msg1.setContentObject(f);
						msg1.setConversationId("factureTransport");
						myAgent.send(msg1);
					} catch (IOException e) {
						System.out.println("Erreur génération de facture");
					}		
				}					
			}
			else{
				block();
			}
		}
	}
	
	//comportement lorsqu'on souhaite réserver la ligne de transport (le rendre indisponible et l'utiliser) 
	//même fonction que ça soit lorsqu'il s'agit du créateur ou d'un autre fournisseur mais pas dans le contexte fin de tour
	//où les créateurs on des droits supplémentaires : cf fonction reserverCreateur() 
	//SI disponible, on se met indisponible, on indique que notre abonne est l'expéditeur du message et on le facture directement
	private class reserver extends OneShotBehaviour{
		private int montant = 0; 
		ACLMessage msg1;
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("demandeReservation"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				//SI la demande provient du créateur ET qu'on ne s'est pas engagé avec un autre fournisseur
				if(msg.getSender() == createur && disponible){
					disponible = false;
					abonne = createur;
					montant = 0;
				}
				//Sinon si la demande provient d'un fournisseur autre
				else{
					if(disponible){
						disponible = false;
						abonne = msg.getSender();
						montant = tarif;
					}
					//Si on nous fait la demande de réservation mais qu'on est indisponible
					else{
						msg1 = new ACLMessage(ACLMessage.REFUSE);
						try {
							msg1.addReceiver(msg.getSender());
							msg1.setContentObject("Transporteur indisponible");
							msg1.setConversationId("reponseReservation");
							myAgent.send(msg1);
						} catch (IOException e) {
							System.out.println("Probleme envoie message refus reservation transporteurFournisseur : " + myAgent.getLocalName());
						}					
					}
					//la demande a été faite en étant disponible, on y répond favorablement que ça soit pourle créateur ou un autre
					msg1 = new ACLMessage(ACLMessage.AGREE);
					try {
						msg1.addReceiver(abonne);
						msg1.setContentObject(montant);
						msg1.setConversationId("reponseReservation");
						myAgent.send(msg1);
					} catch (IOException e) {
						System.out.println("Probleme envoie message acceptation reservation transporteurFournisseur : " + myAgent.getLocalName());
					}		
				}
			}
			else {
				block();
			}
		}
		
	}
	
	/*private class ServiceFacturation extends CyclicBehaviour {

		private int montant = 0;
		private AID receiver;
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("honorerContrat"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				//Si la demande provient du créateur et qu'on a bien que lui comme client
				if (createur == msg.getSender() && abonne == null) {
					montant = 0;
					receiver = createur;
				}
				//Sinon c'est que la demande vient d'un abonné qui n'est pas notre créateur
				else if (abonne == msg.getSender() && abonne != createur){
					montant = tarif;
					receiver = abonne;
				}
				else{
					System.out.println("ERREUR : le transporteurFournisseur :" + myAgent.getLocalName() + " a une demande de facturation d'un fournisseur qui n'est pas son client du tour, le fournisseur : " + msg.getSender());
				}
				
				FactureTransporteur f = new FactureTransporteur(getLocalName(), montant);						
				ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
				try {
					msg1.addReceiver(receiver);
					msg1.setContentObject(f);
					msg1.setConversationId("factureTransport");
					myAgent.send(msg1);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.out.println("Erreur génération de facture");
				}								
			} else {
				block();
			}
		}
	}*/
	
	/*private class ServiceAbonnement extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate
					.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				
				//ACLMessage reply = msg.createReply();
				if(msg.getConversationId() == "abonnementCreateur"){
					createur = msg.getSender();
					abonnes.add(createur);
				}
				//Cas rendu 3 : abonnement d'autres fournisseurs
				//else {
				//	reply.setPerformative(ACLMessage.FAILURE);
				//	reply.setContent("capacité insuffisante");
				//}
				//myAgent.send(reply);
			} else {
				block();
			}

		}
	}*/

	private class ServiceObservateur extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
					MessageTemplate.MatchConversationId("observateur"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage reply = msg.createReply();
				int dateActuelle = (int) System.currentTimeMillis();
				InfoAgent info = new InfoAgent(getLocalName(),
						abonne+ "", CA + "", benefice + "");
				try {
					reply.setContentObject(info);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				myAgent.send(reply);

			} else {
				block();
			}

		}

	}
}
