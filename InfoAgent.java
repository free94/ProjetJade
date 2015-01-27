import java.io.Serializable;


public class InfoAgent implements Serializable {
	public String nom;
	public String nbClient;
	public String CA;
	public String benefice;
	
	public InfoAgent(String nom, String nbClient, String cA,
			String benefice) {
		super();
		this.nom = nom;
		this.nbClient = nbClient;
		CA = cA;
		this.benefice = benefice;
	}

}
