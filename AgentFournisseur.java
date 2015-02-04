import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
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

	// *****************************************LES DONNEES
	// TRANSPORTEURS*****************************************

	private AID transporteurPrincipal = null;// transporteur tierce
												// potentiellement utilisé

	private int tarifTransporteurPrincipal = 0;

	private ArrayList<AID> transporteursUtilises = new ArrayList<AID>();// transporteurs
																		// interne
																		// réservés
																		// sur
																		// le
																		// tour
																		// courant

	private ArrayList<Devis> transporteursDisponibles = new ArrayList<Devis>();// transporteurs
																				// disponibles
																				// au
																				// début
																				// du
																				// tour

	private ArrayList<AID> mesTransporteurs = new ArrayList<AID>();// AID de mes
																	// transporteurs
																	// (créés)

	private static int coutCreationTransporteur = 0;

	private int capaciteTransport = 30; // capacite de transport de son propre
										// agent

	private int tarifTransporteur = 70; // tarif de la location d'une ligne de
										// transport créée par le fournisseur

	// *****************************************LES DONNEES
	// "COMPTABLES"*****************************************
	private int CA;

	private int benefice;

	private static final int amande = 100;// montant d'amande pour une livraison
											// non assurée

	private int nombrePeriodeRentabiliserCreation = 2;// nombre de périodes
														// estimées pour rendre
														// rentable la création
														// d'un transporteur
														// perso

	private static int coutProduction = 1;
	//prix de vente de l'electricié aux clients
	private int prixVente = 10;
	//prix d'achat de l'electricié depuis clients
	private int prixAchat = 2;
	
	private static int periodeFacturation = 1000;

	private HashMap<AID, Abonnement> abonnements = new HashMap<AID, Abonnement>();// liste
																					// des
																					// abonnements,
																					// les
																					// AID
																					// sont
																					// ceux
																					// de
																					// consommateurs

	private ArrayList<FactureTransporteur> facturesTransport = new ArrayList<FactureTransporteur>();

	// -----------------------------------------------------------------------------------------------------------

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

		// Recherche le transporteur principale, on sait qu'il n'y en a qu'un et
		// que cela n'évoluera pas (EDF)
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

		// abonnener au transporteur principal
		// addBehaviour(new AbonnementTransporteur(transporteurPrincipal));

		// inscrire l'agent au horloge
		addBehaviour(new Inscription(horloge, "fournisseur"));
		// on demande ensuite son tarif au kwh : ce prix n'évoluera pas
		addBehaviour(new ServiceDemandePrixFournisseurPrincipal());
		// ajouter le comportement principal qui livre electricité et facture
		// les clients
		addBehaviour(new ServiceTour());
		// ajouter le service pour traiter les demandes de prix
		addBehaviour(new ServiceDemandePrix());
		// ajouter le service pour traiter les demandes d'abonnement
		addBehaviour(new ServiceAbonnement());
		// ajouter le service pour répondre aux demandes observateur
		addBehaviour(new ServiceObservateur());
		// ajout du service de désabonnement d'un consommateur
		addBehaviour(new ServiceDesabonnement());
		// ajout du service pour enregistrer les factures transporteur
		addBehaviour(new ServiceEnregFacture());
		// ajout du service pour recevoir les paiement des factures
		addBehaviour(new ServiceReceptionPaiement());
		// ajouter la vérification des délais de paiement pour les factures tous
		// les 10 secondes
		addBehaviour(new TickerBehaviour(this, 10000) {
			@Override
			protected void onTick() {
				addBehaviour(new VerifPaiement());
			}
		});
		System.out.println("Le fournisseur: " + getAID().getName()
				+ " est prêt.");
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

	public class comparateurDevis implements Comparator<Devis> {
		public int compare(Devis d1, Devis d2) {
			if (Integer.compare(d1.getMontant(), d2.getMontant()) > 0) {
				return -1;
			} else if (Double.compare(d1.getMontant(), d2.getMontant()) < 0) {
				return 1;
			} else {
				return 0;
			}
		}
	}

	private class ServiceDemandePrixFournisseurPrincipal extends
			OneShotBehaviour {

		@Override
		public void action() {
			// Envoi de la demande de prix a tous les fournisseurs
			ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST);
			cfp.addReceiver(transporteurPrincipal);

			cfp.setContent("demandeTarif");
			cfp.setConversationId("demandeTarif-transporteurPrincipal");
			cfp.setReplyWith("request " + System.currentTimeMillis()); // Unique
																		// //
																		// value
			myAgent.send(cfp);
			// Preparer le template pour recevoir la réponse
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchConversationId("reponseTarif-transporteurPrincipal"),
					MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
			ACLMessage msg = myAgent.blockingReceive(mt);
			if (msg != null) {
				// Ce tarif est le tarif unitaire par kwh transporté
				tarifTransporteurPrincipal = Integer.parseInt(msg.getContent());
			} else {
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
					int[] devis = {prixVente,prixAchat};
					try {
						reply.setContentObject(devis);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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

	// comportement quand un consommateur souhaite s'abonner
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
					int[] devis  = {prixVente, prixAchat};
					try {
						reply.setContentObject(devis);
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
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
				capaciteUtilisee -= abonnements.get(msg.getSender())
						.getQuantiteConsommee();
				abonnements.remove(msg.getSender());

			} else {
				block();
			}
		}

	}

	// Service observateur
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

	// réception du message "fin de tour pour les consommateurs" de la part de
	// l'agent horloge -> lancement politique de transport
	// consiste uniquement en une phase de décisions pour le prochain tour,
	// impliquant nos propres transporteurs.
	private class PolitiqueTransport extends CyclicBehaviour {
		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate
					.MatchConversationId("finTourPourConsommateurs"),
					MessageTemplate.MatchSender(horloge));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				// on lance l'analyse pour définir notre politique de transport
				// au prochain round
				int totalAtransporter = 0;
				// premiere étape : calculer la quantité totale d'électricité
				// probablement à transporter étant donné les derniers chiffres
				// des clients
				for (Abonnement a : abonnements.values()) {
					totalAtransporter += a.getQuantiteConsommee();
				}
				transporteursUtilises.clear();
				// deuxième étape on utilise chaque transporteur perso sans
				// réfléchir à condition qu'on utilise 100% de leur capacité
				for (AID monTansporteur : mesTransporteurs) {
					if (capaciteTransport <= totalAtransporter) {
						// on l'indique comme transporteur utilisé
						transporteursUtilises.add(monTansporteur);
						initDisponibilite(false, monTansporteur);
						totalAtransporter -= capaciteTransport;
					} else {
						// calcul de ce qu'il en couterait de passer par un
						// autre transporteur pour ce qu'il reste à transporter
						int cout = tarifTransporteurPrincipal
								* totalAtransporter;
						// trie de notre liste de devis par ordre de montant
						Collections.sort(transporteursDisponibles,
								new comparateurDevis());

						if (transporteursDisponibles.get(0).getMontant() < cout)
							cout = transporteursDisponibles.get(0).getMontant();

						// Si cela est rentable de laisser disponible le
						// transporteur pour le louer, même en louant nous même
						// de quoi transporter
						// ce qu'il reste d'electricité à fournir alors on
						// laisse disponible
						if (tarifTransporteur - cout > 0) {
							// on laisse disponible notre transporteur
							initDisponibilite(true, monTansporteur);
						} else
							initDisponibilite(false, monTansporteur);
					}
				}
			} else {
				block();
			}
		}

		// methode appelée pour indiquer à un de nos transporteurs qu'on le
		// réserve ou pas pour le prochain tour
		private void initDisponibilite(boolean disponibilite,
				AID monTransporteur) {
			// en fonction de ce qui a été décidé, on indique si le transporteur
			// doit se rendre disponible ou s'il est réservé
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

	// comportement gérant le transport de l'electricité poiur les transporteurs
	// tierces (y compirs le principal)
	private class ServiceTransport extends Behaviour {

		// Quantité total à transporter
		int totalAtransporter = 0;
		// Utiliser pour le swith
		int step = 0;
		// Compter le nombre de réponses recus pour les demandes de devis
		int nombreReponse = 0;
		// liste de tous les transporteurs four
		ArrayList<AID> tousLesTransporteursFour;
		// template pour la receptoin de message
		MessageTemplate mt;
		// pour parcourir la liste de transporteur four disponible
		int indiceTransDispo = 0;

		public ServiceTransport(int totalAtransporter) {
			this.totalAtransporter = totalAtransporter;
		}

		@Override
		public void action() {
			switch (step) {
			// Envoie les demandes de devis
			case 0:
				transporteursDisponibles.clear();
				// récupérer tous les transporteurs fournisseurs depuis le DF
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("TransporteurFournisseur");
				template.addServices(sd);
				try {
					DFAgentDescription[] result = DFService.search(myAgent,
							template);
					tousLesTransporteursFour = new ArrayList<AID>();
					for (int i = 0; i < result.length; ++i) {
						tousLesTransporteursFour.add(result[i].getName());
					}
				} catch (FIPAException fe) {
					fe.printStackTrace();
				}
				// Envoie les demandes
				ACLMessage cfp = new ACLMessage(ACLMessage.PROPOSE);
				for (AID a : tousLesTransporteursFour) {
					cfp.addReceiver(a);
				}
				cfp.setContent("demandeTarif");
				cfp.setConversationId("demandeTarif");
				cfp.setReplyWith("cfp" + System.currentTimeMillis()); // Unique
																		// //
																		// value
				myAgent.send(cfp);
				// Preparer le template pour recevoir la réponse
				mt = MessageTemplate.and(MessageTemplate
						.MatchConversationId("tarifTransporteurFournisseur"),
						MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
				step = 1;
				break;
			// Recpetion des devis et mettre les transporteurs disponibles dans
			// la liste
			case 1:
				ACLMessage reply = myAgent.receive(mt);
				if (reply != null) {
					// Le transporteur nous renvoie un message type PROPOSE s'il
					// est disponible
					if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
						// This is an offer
						Devis devis;
						try {
							// Ajouter le devis recu dans notre liste
							devis = (Devis) reply.getContentObject();
							transporteursDisponibles.add(devis);
						} catch (UnreadableException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
					nombreReponse++;
					if (nombreReponse == tousLesTransporteursFour.size()) {
						// Trier la liste des devis selon le tarif
						Collections.sort(transporteursDisponibles,
								new comparateurDevis());
						step = 2;
					}
				} else {
					block();
				}
				break;
			// demander les transporteur fournisseurs à transporter
			case 2:
				if (totalAtransporter > 0) {
					if (indiceTransDispo < transporteursDisponibles.size()) {
						// Essayer de traiter les transport un par un
						// Envoyer une demande d'honoration de contrat pour le
						// transporteur moins cher chaque fois
						Devis cible = transporteursDisponibles
								.get(indiceTransDispo);
						ACLMessage demandeTrans = new ACLMessage(
								ACLMessage.REQUEST);
						demandeTrans.addReceiver(cible.getAidEmetteur());
						demandeTrans.setConversationId("demandeReservation");
						myAgent.send(demandeTrans);
						indiceTransDispo++;
						// Preparer le template pour recevoir la réponse
						mt = MessageTemplate.and(MessageTemplate
								.MatchConversationId("reponseReservation"),
								MessageTemplate.MatchSender(cible
										.getAidEmetteur()));
						step = 3;
					} else {
						// Si l'on a essayer tous les transporteurs fournisseur,
						// passe au transporteur principal
						step = 4;
					}
				} else {
					// Si rien reste à transporter, on passe à étape 4
					step = 4;
				}
				break;
			case 3:
				ACLMessage reponse = myAgent.receive(mt);
				if (reponse != null) {
					// Le transporteur nous renvoie un message type AGREE s'il
					// est disponible
					if (reponse.getPerformative() == ACLMessage.AGREE) {
						totalAtransporter -= capaciteTransport;
						addBehaviour(new ServiceHonorationContrat(
								capaciteTransport, reponse.getSender()));
					}
					step = 2;
				} else {
					block();
				}
				break;
			case 4:
				if (totalAtransporter > 0) {
					// demande le transporteur principal à transporter la reste
					addBehaviour(new ServiceHonorationContrat(
							totalAtransporter, transporteurPrincipal));

					step = 5;
				}
				step = 5;
				break;
			case 5:
				break;
			}

		}

		@Override
		public boolean done() {
			if (step == 5) {
				return true;
			}
			return false;
		}

	}

	// Service pour honorer le contrat avec un fournisseur et lui demander
	// d'éffectuer la livraison
	private class ServiceHonorationContrat extends OneShotBehaviour {
		private int quantite;
		private AID transporteur;

		public ServiceHonorationContrat(int quantite, AID transporteur) {
			this.quantite = quantite;
			this.transporteur = transporteur;
		}

		@Override
		public void action() {
			// on a reservé auparavant des transporteurs qui nous appartiennent,
			// on les utilise en priorité
			ACLMessage msgTrans = new ACLMessage(ACLMessage.REQUEST);
			msgTrans.addReceiver(transporteur);
			msgTrans.setConversationId("honorerContrat");
			// quantité transmis par le transporteur
			msgTrans.setContent(quantite + "");
			myAgent.send(msgTrans);

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
				int quantiteATransporter = capaciteUtilisee;
				if (quantiteATransporter > 0) {
					// on a reservé auparavant des transporteurs qui nous
					// appartiennent, on les utilise en priorité
					for (AID monTransporteur : transporteursUtilises) {
						if (quantiteATransporter > 0) {
							// Si le derniere livraison est pour une quantite
							// inférieur
							// à capaciteTransport, on le facture quand meme
							// comme apaciteTransport
							// puisque les tarifs sont égaux
							addBehaviour(new ServiceHonorationContrat(
									capaciteTransport, monTransporteur));
							quantiteATransporter -= capaciteTransport;
						} else {
							break;
						}
					}

					// Transporter la reste par les tierces
					if (quantiteATransporter > 0) {
						addBehaviour(new ServiceTransport(quantiteATransporter));
					}

					// pour tous les abonnés, emettre les factures qui servent
					// comme des PREUVES DE LIVRAISON
					int dateActuelle = (int) System.currentTimeMillis();
					int vente = 0;// prix total d'une vente d'énergie à un
									// client
					int quantite = 0;
					for (AID a : abonnements.keySet()) {
						int periodeConsommation = dateActuelle
								- abonnements.get(a).getDateAbonnement();
						quantite = (int) abonnements.get(a)
								.getQuantiteConsommee();// quantité
						// à
						// chaque
						// tic
						// d'horloge
						// Calculer le montant de facturation
						Facture f;
						if (periodeConsommation < periodeFacturation) {
							// Si la deriere période de consomation est
							// inférieure à
							// la période de production, facturer la période
							// exacte
							vente = quantite * prixVente * periodeConsommation
									/ 1000;
							f = new Facture(getLocalName(), abonnements.get(a)
									.getDateAbonnement(), abonnements.get(a)
									.getDateAbonnement(), dateActuelle, vente);
							// Le cout de production réduit la bénéfice
							benefice -= quantite * coutProduction
									* periodeConsommation / 1000;
						} else {
							// sinon, facturer sur une période de facturation
							vente = quantite * prixVente * periodeFacturation
									/ 1000;
							f = new Facture(getLocalName(), abonnements.get(a)
									.getDateAbonnement(), dateActuelle
									- periodeFacturation, dateActuelle, vente);
							// Le cout de production réduit la bénéfice
							benefice -= quantite * coutProduction
									* periodeFacturation / 1000;
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
				}

			} else {
				block();
			}
		}
	}

	// TODO modifier cette méthode pour qu'elle fonctionne de la meme maniere
	// comme TransporteurPrincipal
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
						// System.out.println(facture.toString());
						// on paie la facture et ceci dimunue notre bénéfice
						benefice -= facture.getMontant();
						// Envoyer le paiement au transporteur
						if (facture.getMontant() > 0) {
							ACLMessage paiement = new ACLMessage(
									ACLMessage.CONFIRM);
							paiement.setConversationId("paiementFactureTransporteur");
							paiement.addReceiver(msg.getSender());
							paiement.setContent(facture.getMontant() + "");
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

	private class ServiceReceptionPaiement extends CyclicBehaviour {

		@Override
		public void action() {
			MessageTemplate mt = MessageTemplate.and(
					MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
					MessageTemplate.MatchConversationId("paiementFacture"));
			ACLMessage msg = myAgent.receive(mt);
			if (msg != null) {
				CA += Integer.parseInt(msg.getContent());
				benefice += Integer.parseInt(msg.getContent());
				// System.out.println("Client:"+msg.getContent());
				// mettre à jour la date de dernier paiement
				if (abonnements.get(msg.getSender()) == null)
					System.err
							.println("SENDER INTROUVABLE DANS LA LISTE DES ABONNES");
				abonnements.get(msg.getSender()).setDateDernierePaiement(
						(int) System.currentTimeMillis());
			} else {
				block();
			}
		}
	}

	// vérifier les délais de paiement pour les factures
	private class VerifPaiement extends OneShotBehaviour {

		@Override
		public void action() {
			for (AID abonne : abonnements.keySet()) {
				if ((int) System.currentTimeMillis() > 10000 + abonnements.get(
						abonne).getDateDernierePaiement()) {
					benefice -= amande;
					System.out.println("Une amande de " + amande
							+ " est payé à cause d'une livraison non assurée");
					System.err.println(abonnements.get(abonne)
							.getDateDernierePaiement()
							+ "---"
							+ abonne.getLocalName());
				}
			}

		}

	}

}
// comportement gérant l'intégralité de la gestion des transporteurs : création
// d'un trnsporteur perso ou non, etc
/*
 * private class ServiceTransport extends OneShotBehaviour{ private
 * MessageTemplate mt; private double total = 0;
 * 
 * public void action() {
 * 
 * //On vérifie à chaque fois l'ensemble des transporteurs dispo car on peut
 * dans le dernier rendu, choisir le transporteur d'n autre fournisseur //et un
 * autre fournisseur peut décider de créer son propre transporteur à tout moment
 * 
 * //recherche transporteur(s) fournisseur(s) DFAgentDescription template = new
 * DFAgentDescription(); ServiceDescription sd = new ServiceDescription();
 * sd.setType("TransporteurFournisseur"); template.addServices(sd); try {
 * DFAgentDescription[] result = DFService.search(myAgent, template); for
 * (DFAgentDescription r : result) { if(!transporteurs.containsKey(r))
 * transporteurs.put(r.getName(), null); }
 * 
 * } catch (FIPAException fe) { fe.printStackTrace(); }
 * 
 * //********************************ici notre liste de transporteurs est maj
 * 
 * 
 * // le fournisseur souhaite transporter l'ensemble de l'electricité // que ses
 * abonnés lui commandent int capa = capaciteUtilisee; //Si on a ses propres
 * transporteurs, on les privilégie et on leur fait transporter le maximum
 * if(!mesTransporteurs.isEmpty()){ capa -= mesTransporteurs.size() *
 * capaciteTransport; } //Si le transporteur perso peut transporter + que la
 * quantité total qu'on fournit, on arrête ici pas besoin de devis if(capa <=
 * 0){ return; } // Envoi de la demande de prix à tous les transporteurs DONT ON
 * NE // CONNAIT PAS LES TARIFS (ils sont fixes, n'évolueront pas !) int nbMsg =
 * 0; ACLMessage cfp = new ACLMessage(ACLMessage.REQUEST); for (AID t :
 * transporteurs.keySet()) { if (transporteurs.get(t).equals(null)) { // Si le
 * devis est null, on ajoute le transporteur comme un // recepteur
 * cfp.addReceiver(t); nbMsg++; } } cfp.setContent(String.valueOf(capa));
 * cfp.setConversationId("demandeDevis"); cfp.setReplyWith("cfp" +
 * System.currentTimeMillis()); // Unique // // value myAgent.send(cfp); //
 * Preparer le template pour recevoir la réponse mt = MessageTemplate.and(
 * MessageTemplate.MatchConversationId("propositionDevis"),
 * MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
 * 
 * // comparer au coût de création de son propre four : sur une // durée d fixée
 * 
 * int nombreDevis = 0; ACLMessage reply; Devis d = null; Devis devisMoinsCher =
 * new Devis(null,null, 0, 0, Integer.MAX_VALUE); for (int i = 0; i < nbMsg;
 * i++) { //réponse du transporteur reply = myAgent.blockingReceive(mt); if
 * (reply != null) { if (reply.getPerformative() == ACLMessage.PROPOSE) { //
 * récupération du devis try { d = (Devis) reply.getContentObject(); if
 * (d.getMontant()<devisMoinsCher.getMontant()) { devisMoinsCher = d; }
 * transporteurs.put(reply.getSender(), d); } catch (UnreadableException e) {
 * e.printStackTrace(); } } else { block(); } }
 * 
 * } //Choix de la création du transporteur interne ou non if
 * (devisMoinsCher.getMontant() < coutCreationTransporteur /
 * nombrePeriodeRentabiliserCreation) {
 * //System.err.println("------On passe par un transporteur tierce");
 * addBehaviour(new AbonnementTransporteur(devisMoinsCher.getAidEmetteur())); }
 * else { // creation transporteur moins cher
 * //System.err.println("++++++CREATION DE TRANSPORTEUR PERSO"); try { AID[] arg
 * = { myAgent.getAID() }; AgentController monTransporteur =
 * myAgent.getContainerController() .createNewAgent(
 * "AgentTransportFournisseur-" + myAgent.getName(),
 * AgentTransportFournisseur.class .getName(), arg); monTransporteur.start();
 * FactureTransporteur f = new FactureTransporteur( "AgentTransportFournisseur",
 * coutCreationTransporteur); //enregistrement de la facture
 * facturesTransport.add(f); //diminution des benefs benefice -= f.getMontant();
 * // recherche notre transporteur dans le DF template = new
 * DFAgentDescription(); sd = new ServiceDescription();
 * sd.setType("TransporteurFournisseur");
 * sd.setName("AgentTransportFournisseur-" + myAgent.getName());
 * template.addServices(sd);
 * 
 * // on enregistre l'AID de notre agent de transport DFAgentDescription[]
 * result = DFService.search(myAgent, template);
 * mesTransporteurs.add(result[0].getName());
 * transporteursUtilises.add(result[0].getName()); } catch (StaleProxyException
 * e) { // TODO Auto-generated catch block e.printStackTrace(); } catch
 * (FIPAException e) { // TODO Auto-generated catch block e.printStackTrace(); }
 * } } }
 */