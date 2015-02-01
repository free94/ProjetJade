import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

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
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

public class AgentFournisseur extends Agent {
	/**
	 * Agent fournisseur
	 * 
	 * -1h dans le programme = 1s dans la vie réel -Les dates sont réprésentés
	 * en unité de milliseconde
	 */
	// //les constantes pour observateurs
	// private final static int obListeAbonnes = 0;
	// private final static int obCA = 1;
	// private final static int obBenefice = 2;

	private int capaciteProduction = 999;
	
	private int capaciteUtilisee = 0;// la capacité utilisé actuellement
	
	private AID horloge = null;// l'agent horloge
		
	//*****************************************LES DONNEES TRANSPORTEURS*****************************************
	
	private AID transporteurPrincipal = null;// transporteur tierce potentiellement utilisé
	
	private int tarifTransporteurPrincipal = 0;
	
	private ArrayList<AID> transporteursUtilises = new ArrayList<AID>();// transporteurs utilisés sur le tour courant
	
	private ArrayList<Devis> transporteursDisponibles = new ArrayList<Devis>();// transporteurs disponibles au début du tour
	
	private ArrayList<AID> mesTransporteurs = new ArrayList<AID>();//AID de mes transporteurs (créés) 
	
	private static int coutCreationTransporteur = 0;
	
	private int capaciteTransport = 30;	//capacite de transport de son propre agent
	
	private int tarifTransporteur = 70; //tarif de la location d'une ligne de transport créée par le fournisseur
			
	//*****************************************LES DONNEES "COMPTABLES"*****************************************
	private int CA;
	
	private int benefice;
	
	private static final int amande = 100;//montant d'amande pour une livraison non assurée
	
	private int nombrePeriodeRentabiliserCreation = 2;//nombre de périodes estimées pour rendre rentable la création d'un transporteur perso
	
	private static int coutProduction = 1;
	
	private static int prixVente = 10;
	
	private static int periodeFacturation = 1000;
	
	private HashMap<AID, Abonnement> abonnements = new HashMap<AID, Abonnement>();// liste des abonnements, les AID sont ceux de consommateurs
	
	private ArrayList<FactureTransporteur> facturesTransport = new ArrayList<FactureTransporteur>();
	//-----------------------------------------------------------------------------------------------------------
	
	protected void setup() {
		// Enregistrement du service dans le DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Fournisseur");
		sd.setName(getAID().getLocalName());
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
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
		
		//Recherche transporteur tierce, on sait qu'il n'y en a qu'un et que cela n'évoluera pas (EDF)
		template = new DFAgentDescription();
		sd = new ServiceDescription();
		sd.setType("TransporteurPrincipal");
		template.addServices(sd);
		try {
			result = DFService.search(this, template);
			transporteurPrincipal = result[0].getName();
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}		
		
		//on demande ensuite son tarif au kwh : ce prix n'évoluera pas 
		addBehaviour(new ServiceDemandePrixFournisseurPrincipal());
		
		// abonnener au transporteur principal
		//addBehaviour(new AbonnementTransporteur(transporteurPrincipal));
		
		// inscrire l'agent au horloge
		addBehaviour(new Inscription(horloge, "fournisseur"));
		// ajouter le comportement principal qui livre electricité et facture
		// les clients
		addBehaviour(new ServiceTour());
		// ajouter le service pour traiter les demandes de prix
		addBehaviour(new ServiceDemandePrix());
		// ajouter le service pour traiter les demandes d'abonnement
		addBehaviour(new ServiceAbonnement());
		// ajouter le service pour répondre aux demandes observateur
		addBehaviour(new ServiceObservateur());
		// ajout du service de recherche de la meilleure solution de transport
		addBehaviour(new ServiceTransport());
		// ajout du service de désabonnement d'un consommateur
		addBehaviour(new ServiceDesabonnement());
		// ajout du service pour enregistrer les factures transporteur
		addBehaviour(new ServiceEnregFacture());
		//ajout du service pour recevoir les paiement des factures
		addBehaviour(new ServiceReceptionPaiement());
		//ajouter la vérification des délais de paiement pour les factures tous les 10 secondes
		addBehaviour(new TickerBehaviour(this,10000){
			@Override
			protected void onTick() {
				addBehaviour(new VerifPaiement());
			}});
		addBehaviour(new TickerBehaviour(this,100000){
			@Override
			protected void onTick() {
				addBehaviour(new ServiceTransport());
			}});
	}

	protected void takeDown() {
		// Deregister from the yellow pages
		try {
			DFService.deregister(this);
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		// Printout a dismissal message
		System.out.println("Le fournisseur: " + getAID().getName()
				+ " est terminé.");
	}
	public class comparateurDevis implements Comparator<Devis>{
		public int compare(Devis d1, Devis d2){
			if (Integer.compare(d1.getMontant(), d2.getMontant()) > 0) {
				return -1;
			} else if (Double.compare(d1.getMontant(), d2.getMontant()) < 0) {
				return 1;        	
			} else {
				return 0;
			}
		}
	}
	
	private class ServiceDemandePrixFournisseurPrincipal extends OneShotBehaviour{

		@Override
		public void action() {
			// Envoi de la demande de prix a tous les fournisseurs
			ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
			cfp.addReceiver(transporteurPrincipal);
			
			cfp.setContent("demandeTarif");
			cfp.setConversationId("demandeTarif-transporteurPrincipal");
			cfp.setReplyWith("request " + System.currentTimeMillis()); // Unique																		// value
			myAgent.send(cfp);
			// Preparer le template pour recevoir la réponse
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("reponseTarif-transporteurPrincipal"),
					MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
			ACLMessage msg = myAgent.receive(mt);
			
			if (msg != null) {
				try {
					tarifTransporteurPrincipal = (Integer) msg.getContentObject();
				} catch (UnreadableException e) {
					e.printStackTrace();
				}					
			} 
			else {
				block();
			}	
		}
	}
	
	// comportement du fournisseur quand il reçoit une demande du consommateur
	// concernant ses tarifs
	private class ServiceDemandePrix extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.CFP),
					MessageTemplate.MatchConversationId("tarif-energie"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// CFP Message received. Process it
				String title = msg.getContent();
				ACLMessage reply = msg.createReply();
				if (capaciteProduction > capaciteUtilisee) {
					// si on dispose encore de capacité, répondre notre prix
					reply.setPerformative(ACLMessage.PROPOSE);
					reply.setContent("" + prixVente);
				} else {
					reply.setPerformative(ACLMessage.REFUSE);
					reply.setContent("capacité saturé");
				}
				myAgent.send(reply);
			} else {
				block();
			}
		}
	}
	
	//comportement quand un consommateur souhaite s'abonner
	private class ServiceAbonnement extends CyclicBehaviour {

		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL),
					MessageTemplate.MatchConversationId("abonnement"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// quantité demandée par consommateur
				int consommation = Integer.parseInt(msg.getContent());
				ACLMessage reply = msg.createReply();

				if (capaciteProduction - capaciteUtilisee >= consommation) {
					// Si on dispose encore assez de capacité, on accepte
					// abonnement
					reply.setPerformative(ACLMessage.INFORM);
					abonnements.put(msg.getSender(),
							new Abonnement((int) System.currentTimeMillis(),
									Integer.parseInt(msg.getContent())));
					capaciteUtilisee += consommation;
					System.out.println("un consommateur "
							+ msg.getSender().getLocalName()
							+ " est abonné pour une quantité de "
							+ consommation);
				} else {
					reply.setPerformative(ACLMessage.FAILURE);
					reply.setContent("capacité insuffisante");
				}
				myAgent.send(reply);
			} else {
				block();
			}

		}
	}

	// Desabonnement d'un consommateur : autrement dit suppression de ce client
	// dans la liste des abonnés
	private class ServiceDesabonnement extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId("desabonnement"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				capaciteUtilisee-=abonnements.get(msg.getSender()).getQuantite();
				abonnements.remove(msg.getSender());
				
			} else {
				block();
			}
		}

	}

	//Service observateur
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
						abonnements.size() + "", CA + "", benefice + "");
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
	
	//réception du message "fin de tour pour les consommateurs" de la part de l'agent horloge -> lancement politique de transport
	//consiste uniquement en une phase de décisions pour le prochain tour, impliquant nos propres transporteurs.
	private class PolitiqueTransport extends CyclicBehaviour{
		@Override
		public void action(){
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("finTourPourConsommateurs"),
					MessageTemplate.MatchSender(horloge));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				//on lance l'analyse pour définir notre politique de transport au prochain round
				int totalAtransporter = 0;
				//premiere étape : calculer la quantité totale d'électricité probablement à transporter étant donné les derniers chiffres des clients
				for (Abonnement a : abonnements.values()) {
					totalAtransporter += a.getQuantite();
				}
				transporteursUtilises.clear();
				//deuxième étape on utilise chaque transporteur perso sans réfléchir à condition qu'on utilise 100% de leur capacité
				for (AID monTansporteur : mesTransporteurs) {
					if(capaciteTransport <= totalAtransporter){
						//on l'indique comme transporteur utilisé
						transporteursUtilises.add(monTansporteur);
						initDisponibilite(false, monTansporteur);
						totalAtransporter -= capaciteTransport;
					}
					else{
						//calcul de ce qu'il en couterait de passer par un autre transporteur pour ce qu'il reste à transporter
						int cout = tarifTransporteurPrincipal * totalAtransporter;
						//trie de notre liste de devis par ordre de montant
						Collections.sort(transporteursDisponibles, new comparateurDevis());
						
						if(transporteursDisponibles.get(0).getMontant() < cout)
							cout = transporteursDisponibles.get(0).getMontant();
						
						//Si cela est rentable de laisser disponible le transporteur pour le louer, même en louant nous même de quoi transporter 
						//ce qu'il reste d'electricité à fournir alors on laisse disponible 
						if(tarifTransporteur - cout > 0){
							//on laisse disponible notre transporteur
							initDisponibilite(true, monTansporteur);							
						}
						else
							initDisponibilite(false, monTansporteur);
					}						
				}				
			}
			else{
				block();
			}
		}
		//methode appelée pour indiquer à un de nos transporteurs qu'on le réserve ou pas pour le prochain tour 
		private void initDisponibilite(boolean disponibilite, AID monTransporteur){
			//en fonction de ce qui a été décidé, on indique si le transporteur doit se rendre disponible ou s'il est réservé
			ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
			cfp.addReceiver(monTransporteur);
			try {
				cfp.setContentObject(disponibilite);
			} catch (IOException e) {
				e.printStackTrace();
			}
			cfp.setContent("reservationProchainRound");
			cfp.setConversationId("initDisponibilite");
			myAgent.send(cfp);
		}
	}
	
	//comportement gérant le transport de l'electricité pour le tour
	private class ServiceTransport extends CyclicBehaviour{
		int totalAtransporter = 0;
		ArrayList<AID> tousLesTransporteursFour = null;
		@Override
		public void action() {
			//calcul du total d'electricité à transporter
			for (Abonnement a : abonnements.values()) {
				totalAtransporter += a.getQuantite();
			}
			//on a reservé auparavant des transporteurs qui nous appartiennent, on les utilise en priorité
			totalAtransporter -= capaciteTransport * transporteursUtilises.size();//transporteursUtilises ne contient pour l'instant QUE des transporteurs perso
			//s'il reste de l'electricité à transporter sous entendu nos propres transporteurs ne suffisent pas
			if(totalAtransporter > 0){
				//récupération des tarifs de tous les transporteurs fournisseurs DISPONIBLES connus dans le DF
				transporteursDisponibles.clear();
				transporteursDisponibles = maj(transporteursDisponibles);
				
				MessageTemplate mt = MessageTemplate.MatchConversationId("reponseReservation");
				//à la première réponse traitée on arrête on sort du for ou directement si on voit que rien n'est moins cher que le transporteur principal
				
				for (Devis d : transporteursDisponibles) {
					if(d.getMontant() > tarifTransporteurPrincipal * totalAtransporter){
						//traitement du transport avec le transporteur principal
						/*TODO
						 * Abonnement ou pas ?
						 * Direct demande d'honorer contrat ?
						 * */
						break;
					}
					else{
						//demande de réservation du transporteur car moins cher que le transporteur principal
						/*TODO
						 * Si c'est accecpté
						 * Si c'est refusé
						 * */
						totalAtransporter -= d.getQuantiteElectricite();
						if(totalAtransporter <= 0)
							break;
					}
				}
				
			}
		}
		
		private ArrayList<Devis> maj(ArrayList<Devis> transporteursDisponibles){
			
			//maj depuis le DF de tous les transporteurs fournisseurs disponibles
			DFAgentDescription template = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("TransporteurFournisseur");
			template.addServices(sd);
			try {
				DFAgentDescription[] result = DFService.search(myAgent, template);
				tousLesTransporteursFour = new ArrayList<AID>();
				for (int i = 0; i < result.length; ++i) {
					tousLesTransporteursFour.add(result[i].getName());
				}
			} catch (FIPAException fe) {
				fe.printStackTrace();
			}
			
			//envoie d'une demande de devis à tous les transporteurs fournisseurs : si réponse => dispo, sinon = indispo
			ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
			for (AID a : tousLesTransporteursFour) {
				cfp.addReceiver(a);
			}
			cfp.setContent("demandeTarif");
			cfp.setConversationId("demandeTarif");
			cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique																		// value
			myAgent.send(cfp);
			// Preparer le template pour recevoir la réponse
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("tarifTransporteurFournisseur"),
					MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
			
			//obtention des réponses
			ACLMessage reply = myAgent.receive(mt);
			int nombreReponses = 0;
			if (reply != null) {
				nombreReponses++;
				if(reply.getContent().equals("disponible")){
					try {
						transporteursDisponibles.add((Devis) reply.getContentObject());
					} catch (UnreadableException e) {
						e.printStackTrace();
					}
				}
			} 
			else {
				block();
			}
			Collections.sort(transporteursDisponibles, new comparateurDevis());
			return transporteursDisponibles;
		}
		
	}
	
	// Comportement "métier", contient la vérification contrat avec le
	// transporteur, la facturation
	private class ServiceTour extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchConversationId("msgDebutTour"),
					MessageTemplate.MatchSender(horloge));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				ACLMessage msgTrans;
				int quantite = capaciteUtilisee;
				//Si on a un transporteur perso : il transportera le max qu'il peut et le transporteur tierce ne transportera que le reste
				if(monTransporteur != null){
					// Honorer le contrat avec son transporteur
					msgTrans = new ACLMessage(ACLMessage.REQUEST);
					msgTrans.addReceiver(monTransporteur);								
					msgTrans.setConversationId("honorerContrat");					
					//quantité transmis par le transporteur
					quantite = capaciteTransport;				
					msgTrans.setContent(quantite+"");
					myAgent.send(msgTrans);
					
					//Si notre transporteur perso ne suffit pas et qu'on a un transporteur tierce
					if(capaciteUtilisee > capaciteTransport){
						quantite = capaciteUtilisee - capaciteTransport;
					}
					else
						quantite = 0;
					
				}
				//si on a pas de transporteur perso DONC forcément un transporteur tierce OU si on avait un perso mais qu'il ne suffit pas
				if(transporteurTierce != null){
					// Honorer le contrat avec son transporteur
					msgTrans = new ACLMessage(ACLMessage.REQUEST);
					msgTrans.addReceiver(transporteurTierce);					
					msgTrans.setConversationId("honorerContrat");
					//dans des cas très particuliers on peut n'avoir rien à demander à transporter au transporteurTierce : il faut néanmoins une facture
					msgTrans.setContent(quantite+"");// quantité peut valoir 0
					myAgent.send(msgTrans);
				}
				
				// pour tous les abonnés, emettre les factures qui servent
				// comme des PREUVES DE LIVRAISON
				int dateActuelle = (int) System.currentTimeMillis();
				int vente = 0;// prix total d'une vente d'énergie à un client
				for (AID a : abonnements.keySet()) {
					int periodeConsommation = dateActuelle
							- abonnements.get(a).getDateAbonnement();
					quantite = (int) abonnements.get(a).getQuantite();//quantité à chaque tic d'horloge
					// Calculer le montant de facturation
					Facture f;
					if (periodeConsommation < periodeFacturation) {
						// Si la deriere période de consomation est inférieure à
						// la période de production, facturer la période exacte
						vente = quantite*prixVente * periodeConsommation / 1000;
						f = new Facture(getLocalName(), abonnements.get(a)
								.getDateAbonnement(), abonnements.get(a)
								.getDateAbonnement(), dateActuelle, vente);
						//Le cout de production réduit la bénéfice
						benefice -= quantite*coutProduction * periodeConsommation / 1000;
					} else {
						// sinon, facturer sur une période de facturation
						vente = quantite*prixVente
						* periodeFacturation / 1000;
						f = new Facture(getLocalName(), abonnements.get(a)
								.getDateAbonnement(), dateActuelle
								- periodeFacturation, dateActuelle, vente);
						//Le cout de production réduit la bénéfice
						benefice -= quantite*coutProduction * periodeFacturation / 1000;
					}
					ACLMessage msg1 = new ACLMessage(ACLMessage.INFORM);
					msg1.addReceiver(a);

					try {
						msg1.setContentObject(f);
						msg1.setConversationId("facture");
						myAgent.send(msg1);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						System.err.println("Erreur génération de facture");
					}

				}
				// Envoyer le message fin de tour
				ACLMessage msgFinDeTour = new ACLMessage(ACLMessage.INFORM);
				msgFinDeTour.setConversationId("msgFinDeTour");
				msgFinDeTour.addReceiver(horloge);
				myAgent.send(msgFinDeTour);
			} else {
				block();
			}

		}
	}

	private class AbonnementTransporteur extends OneShotBehaviour {
		private AID transporteur;
		
		public AbonnementTransporteur(AID transporteur) {
			this.transporteur = transporteur;
		}
		@Override
		public void action() {
			// Abonnement
			ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
			msg.addReceiver(transporteur);
			msg.setConversationId("abonnementTransporteur");
			myAgent.send(msg);
			transporteursUtilises.add(transporteur);
		}
	}
	//TODO modifier cette méthode pour qu'elle fonctionne de la meme maniere comme TransporteurPrincipal
	// Enregistrement des factures et ses paiement
	private class ServiceEnregFacture extends CyclicBehaviour {
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.INFORM),
					MessageTemplate.MatchConversationId("factureTransport"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {

				try {
					FactureTransporteur facture = (FactureTransporteur) msg
							.getContentObject();
					if (null != facture) {
						facturesTransport.add(facture);
						//System.out.println(facture.toString());
						//on paie la facture et ceci dimunue notre bénéfice
						benefice -= facture.getMontant();
						//Envoyer le paiement au transporteur
						if (facture.getMontant()>0) {
							ACLMessage paiement = new ACLMessage(ACLMessage.CONFIRM);
							paiement.setConversationId("paiementFactureTransporteur");
							paiement.addReceiver(msg.getSender());
							paiement.setContent(facture.getMontant()+"");
							myAgent.send(paiement);
						}
					} else {
						System.out
								.println("Erreur: la facture recu ne contient aucune information");
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			} else {
				block();
			}
		}
	}

	private class ServiceReceptionPaiement extends CyclicBehaviour{

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
					MessageTemplate.MatchConversationId("paiementFacture"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				CA += Integer.parseInt(msg.getContent());
				benefice += Integer.parseInt(msg.getContent());
				//System.out.println("Client:"+msg.getContent());
				//mettre à jour la date de dernier paiement
				if(abonnements.get(msg.getSender()) == null)
					System.err.println("SENDER INTROUVABLE DANS LA LISTE DES ABONNES");
				abonnements.get(msg.getSender()).setDateDerniereFacture((int)System.currentTimeMillis());
			} else {
				block();
			}	
		}
	}

	//vérifier les délais de paiement pour les factures
	private class VerifPaiement extends OneShotBehaviour{
		
		@Override
		public void action() {
			for (AID abonne: abonnements.keySet()) {
				if ((int)System.currentTimeMillis()>100000+abonnements.get(abonne).getDateDernierePaiement()) {
					benefice-=amande;
					System.out.println("Une amande de "+amande+" est payé à cause d'une livraison non assurée");
				}
			}
			
		}
		
	}
	
	
}
//comportement gérant l'intégralité de la gestion des transporteurs : création d'un trnsporteur perso ou non, etc
/*private class ServiceTransport extends OneShotBehaviour{
	private MessageTemplate mt;
	private double total = 0;
	
	public void action() {
		
		//On vérifie à chaque fois l'ensemble des transporteurs dispo car on peut dans le dernier rendu, choisir le transporteur d'n autre fournisseur
		//et un autre fournisseur peut décider de créer son propre transporteur à tout moment
				
		//recherche transporteur(s) fournisseur(s)
		DFAgentDescription template = new DFAgentDescription();
		ServiceDescription sd = new ServiceDescription();
		sd.setType("TransporteurFournisseur");
		template.addServices(sd);
		try {
			DFAgentDescription[] result = DFService.search(myAgent, template);
			for (DFAgentDescription r : result) {
				if(!transporteurs.containsKey(r))
					transporteurs.put(r.getName(), null);
			}			
			
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}
		
		//********************************ici notre liste de transporteurs est maj
		
				
		// le fournisseur souhaite transporter l'ensemble de l'electricité
		// que ses abonnés lui commandent
		int capa = capaciteUtilisee;
		//Si on a ses propres transporteurs, on les privilégie et on leur fait transporter le maximum
		if(!mesTransporteurs.isEmpty()){
			capa -= mesTransporteurs.size() * capaciteTransport;			
		}
		//Si le transporteur perso peut transporter + que la quantité total qu'on fournit, on arrête ici pas besoin de devis		
		if(capa <= 0){				
			return;
		}
		// Envoi de la demande de prix à tous les transporteurs DONT ON NE
		// CONNAIT PAS LES TARIFS (ils sont fixes, n'évolueront pas !)
		int nbMsg = 0;
		ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
		for (AID t : transporteurs.keySet()) {
			if (transporteurs.get(t).equals(null)) {
				// Si le devis est null, on ajoute le transporteur comme un
				// recepteur
				cfp.addReceiver(t);
				nbMsg++;
			}
		}
		cfp.setContent(String.valueOf(capa));
		cfp.setConversationId("demandeDevis");
		cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique //
																// value
		myAgent.send(cfp);
		// Preparer le template pour recevoir la réponse
		mt = MessageTemplate.and(
				MessageTemplate.MatchConversationId("propositionDevis"),
				MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

			// comparer au coût de création de son propre four : sur une
			// durée d fixée
			
			int nombreDevis = 0;
			ACLMessage reply;
			Devis d = null;
			Devis devisMoinsCher = new Devis(null,null, 0, 0, Integer.MAX_VALUE);
			for (int i = 0; i < nbMsg; i++) {
				//réponse du transporteur
				reply = myAgent.blockingReceive(mt);
				if (reply != null) {
					if (reply.getPerformative() == ACLMessage.PROPOSE) {
					// récupération du devis
						try {
							d = (Devis) reply.getContentObject();
							if (d.getMontant()<devisMoinsCher.getMontant()) {
								devisMoinsCher = d;
							}
							transporteurs.put(reply.getSender(), d);
						} catch (UnreadableException e) {
							e.printStackTrace();
						}
					}
					else {
						block();
					}
				}
				
			}
			//Choix de la création du transporteur interne ou non
			if (devisMoinsCher.getMontant() < coutCreationTransporteur / nombrePeriodeRentabiliserCreation) {
				//System.err.println("------On passe par un transporteur tierce");
				addBehaviour(new AbonnementTransporteur(devisMoinsCher.getAidEmetteur()));
			} else {
				// creation transporteur moins cher				
				//System.err.println("++++++CREATION DE TRANSPORTEUR PERSO");
				try {
					AID[] arg = { myAgent.getAID() };
					AgentController monTransporteur = myAgent.getContainerController()
							.createNewAgent(
									"AgentTransportFournisseur-"
											+ myAgent.getName(),
									AgentTransportFournisseur.class
											.getName(), arg);
					monTransporteur.start();
					FactureTransporteur f = new FactureTransporteur(
							"AgentTransportFournisseur",
							coutCreationTransporteur);
					//enregistrement de la facture
					facturesTransport.add(f);
					//diminution des benefs
					benefice -= f.getMontant();
					// recherche notre transporteur dans le DF
					template = new DFAgentDescription();
					sd = new ServiceDescription();
					sd.setType("TransporteurFournisseur");
					sd.setName("AgentTransportFournisseur-"
							+ myAgent.getName());
					template.addServices(sd);

					// on enregistre l'AID de notre agent de transport
					DFAgentDescription[] result = DFService.search(myAgent,
							template);
					mesTransporteurs.add(result[0].getName());
					transporteursUtilises.add(result[0].getName());
				} catch (StaleProxyException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (FIPAException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}					
			}
	}
}*/