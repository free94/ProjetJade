import java.io.Serializable;

public class InfoAgent implements Serializable {
	public String nom;
	public String nbClient;
	public String CA;
	public String benefice;
	public String prixVenteOuTransport;
	public String prixAchatOuCapaTrans;

	public InfoAgent(String nom, String nbClient, String cA, String benefice,
			String prixVenteOuTransport, String prixAchatOuCapaTrans) {
		super();
		this.nom = nom;
		this.nbClient = nbClient;
		this.CA = cA;
		this.benefice = benefice;
		this.prixVenteOuTransport = prixVenteOuTransport;
		this.prixAchatOuCapaTrans = prixAchatOuCapaTrans;
	}

}
