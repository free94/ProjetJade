import java.io.Serializable;

public class Facture implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String fournisseur;
	private int dateAbonnement;
	private int periodeDebut;
	private int periodeFin;
	private double montant;
	private int prixVente;
	private int prixAchat;

	public Facture(String fournisseur, int dateAbonnement, int periodeDebut,
			int periodeFin, double montant, int prixVente, int prixAchat) {
		this.fournisseur = fournisseur;
		this.dateAbonnement = dateAbonnement;
		this.periodeDebut = periodeDebut;
		this.periodeFin = periodeFin;
		this.montant = montant;
		this.prixVente = prixVente;
		this.prixAchat = prixAchat;

	}

	public double getMontant() {
		return montant;
	}

	@Override
	public String toString() {
		return "Facture " + fournisseur + ": [date de début d'abonnement ="
				+ dateAbonnement + ", montant de la période " + periodeDebut
				+ " à " + periodeFin + " =" + montant + "]";
	}

	public int getPrixVente() {
		return prixVente;
	}

	public int getPrixAchat() {
		return prixAchat;
	}

}
