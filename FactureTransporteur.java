import java.io.Serializable;

public class FactureTransporteur implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String transporteur;
	private int montant;

	public FactureTransporteur(String transporteur, int montant) {
		this.transporteur = transporteur;
		this.montant = montant;
	}

	public int getMontant() {
		return montant;
	}

	@Override
	public String toString() {
		return "Facture " + transporteur + ": [montant: " + montant + "]";
	}

}
