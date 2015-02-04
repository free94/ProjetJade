import jade.core.AID;


public class Abonnement {
//just a test
	private int dateAbonnement;
	private double quantiteConsommee;
	private double quantiteConsommePrevuee;
	private int quantiteProduite;
	private int dateDernierePaiement;
	
	
	public Abonnement(int dateAbonnement, double quantiteConsommee) {
		super();
		this.dateAbonnement = dateAbonnement;
		this.quantiteConsommee = quantiteConsommee;
		this.quantiteConsommePrevuee = 0;
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



	public double getQuantiteConsommePrevuee() {
		return quantiteConsommePrevuee;
	}


	public void setQuantiteConsommePrevuee(double quantiteConsommePrevuee) {
		this.quantiteConsommePrevuee = quantiteConsommePrevuee;
	}

	
}
