import jade.core.AID;


public class Abonnement {
	private int dateAbonnement;
	private double quantite;
	private int dateDernierePaiement;
	
	
	public Abonnement(int dateAbonnement, double quantite) {
		super();
		this.dateAbonnement = dateAbonnement;
		this.quantite = quantite;
		this.dateAbonnement = (int) System.currentTimeMillis();
	}


	public int getDateAbonnement() {
		return dateAbonnement;
	}


	public void setDateAbonnement(int dateAbonnement) {
		this.dateAbonnement = dateAbonnement;
	}


	public double getQuantite() {
		return quantite;
	}


	public int getDateDernierePaiement() {
		return dateDernierePaiement;
	}


	public void setDateDerniereFacture(int dateDernierePaiement) {
		this.dateDernierePaiement = dateDernierePaiement;
	}


	public void setQuantite(double quantite) {
		this.quantite = quantite;
	}
	
}
