import jade.core.AID;

import java.io.Serializable;

public class Devis implements Serializable {

	private int numeroFacture;
	private AID aidEmetteur;
	private AID aid;
	private int quantiteElectricite;// transportable (capacité max) pour les
									// transporteurs de fournisseurs, ou à
									// transporter pour le transporteur
									// principal
	private int date;
	private int montant;

	public Devis(AID aid, AID aidEmetteur, int quantiteElectricite, int date,
			int montant) {
		super();
		this.aid = aid;
		this.aidEmetteur = aidEmetteur;
		this.quantiteElectricite = quantiteElectricite;
		this.date = date;
		this.montant = montant;
		this.nombreFacture++;
		this.numeroFacture = nombreFacture;
	}

	@Override
	public String toString() {
		return "Devis " + numeroFacture + "du " + date
				+ " : \npour le fournisseur : " + aid + "\nQuantite : "
				+ quantiteElectricite + " Montant : " + montant;
	}

	private static int nombreFacture;

	public static int getNombreFacture() {
		return nombreFacture;
	}

	public static void setNombreFacture(int nombreFacture) {
		Devis.nombreFacture = nombreFacture;
	}

	public int getNumeroFacture() {
		return numeroFacture;
	}

	public void setNumeroFacture(int numeroFacture) {
		this.numeroFacture = numeroFacture;
	}

	public AID getAid() {
		return aid;
	}

	public void setAid(AID aid) {
		this.aid = aid;
	}

	public int getQuantiteElectricite() {
		return quantiteElectricite;
	}

	public void setQuantiteElectricite(int quantiteElectricite) {
		this.quantiteElectricite = quantiteElectricite;
	}

	public int getDate() {
		return date;
	}

	public void setDate(int date) {
		this.date = date;
	}

	public int getMontant() {
		return montant;
	}

	public void setMontant(int montant) {
		this.montant = montant;
	}

	public AID getAidEmetteur() {
		return aidEmetteur;
	}

	public void setAidEmetteur(AID aidEmetteur) {
		this.aidEmetteur = aidEmetteur;
	}

}
