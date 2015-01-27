import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
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

	// la puissance max d'électricité qu'un fournissuer est capable de fournir
	// dans une unité de temps
	private int capaciteProduction = 999;
	// la capacité utilisé actuellement
	private int capaciteUtilisee = 0;
	// prix de production en KWh
	private static int coutProduction = 1;
	// prix de vente d'électricité, en KWh
	private static int prixVente = 10;
	// unité en milliseconde
	private static int periodeFacturation = 1000;
	// liste des abonnements, les AID sont ceux de consommateurs
	private HashMap<AID, Abonnement> abonnements = new HashMap<AID, Abonnement>();
	// transporteur tierce potentiellement utilisé
	private HashMap<AID, Devis> transporteurs = new HashMap<AID, Devis>();
	// transpoteurs utilisés
	private ArrayList<AID> transporteursUtilises = new ArrayList<AID>();
	// l'agent horloge
	private AID horloge = null;
	// Factures transporteur
	private ArrayList<FactureTransporteur> facturesTransport = new ArrayList<FactureTransporteur>();
	// cout creation de son propre agent de transport
	private static int coutCreationTransporteur = 0;
	//capacite de transport de son propre agent
	private int capaciteTransport = 30;
	//AID de mon transporteur
	private ArrayList<AID> mesTransporteurs = new ArrayList<AID>(); 
	// chiffre d'affaire
	private int CA;
	// bénéfice (Résultat net avant impôt)
	private int benefice;
	//montant d'amande pour une livraison non assurée
	private static final int amande = 100;
	//nombre de période estimée pour rendre rentable la création d'un transporteur perso
	private int nombrePeriodeRentabiliserCreation = 2;


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
		sd.setType("Transporteur");
		template.addServices(sd);
		AID transporteurPrincipal = null;
		try {
			result = DFService.search(this, template);
			transporteurs.put(result[0].getName(), null);
			transporteurPrincipal = result[0].getName();
		} catch (FIPAException fe) {
			fe.printStackTrace();
		}		
		
		
		// abonnener au transporteur principal
		addBehaviour(new AbonnementTransporteur(transporteurPrincipal));
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
	
	//comportement gérant l'intégralité de la gestion des transporteurs : création d'un trnsporteur perso ou non, etc
	private class ServiceTransport extends OneShotBehaviour{
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
