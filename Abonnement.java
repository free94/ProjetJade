import jade.core.AID;

public class Abonnement {
	// just a test
	private int dateAbonnement;
	private double quantiteConsommee;
	private double quantiteConsommeePrevuee;
	private int quantiteProduite;
	private int dateDernierePaiement;

	public Abonnement(int dateAbonnement, double quantiteConsommee,
			double quantiteConsommeePrevuee) {
		super();
		this.dateAbonnement = dateAbonnement;
		this.quantiteConsommee = quantiteConsommee;
		this.quantiteConsommeePrevuee = quantiteConsommeePrevuee;
		this.quantiteProduite = 0;
		this.dateDernierePaiement = (int) System.currentTimeMillis();
	}

	public int getDateAbonnement() {
		return dateAbonnement;
	}

	public void setDateAbonnement(int dateAbonnement) {
		this.dateAbonnement = dateAbonnement;
	}

	public double getQuantiteConsommee() {
		return quantiteConsommee;
	}

	public void setQuantiteConsommee(double quantiteConsommee) {
		this.quantiteConsommee = quantiteConsommee;
	}

	public int getQuantiteProduite() {
		return quantiteProduite;
	}

	public void setQuantiteProduite(int quantiteProduite) {
		this.quantiteProduite = quantiteProduite;
	}

	public void setDateDernierePaiement(int dateDernierePaiement) {
		this.dateDernierePaiement = dateDernierePaiement;
	}

	public int getDateDernierePaiement() {
		return dateDernierePaiement;
	}

	public double getQuantiteConsommeePrevuee() {
		return quantiteConsommeePrevuee;
	}

	public void setQuantiteConsommeePrevuee(double quantiteConsommePrevuee) {
		this.quantiteConsommeePrevuee = quantiteConsommePrevuee;
	}

}
