import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.util.Arrays;
import java.util.Comparator;

import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

//import examples.bookTrading.BookSellerAgent;

public class AgentObservateurGUI extends JFrame {

	private static final long serialVersionUID = 1L;
	// composant interface
	private JPanel jContentPane = null;
	private JScrollPane scrollPaneFour;
	private JScrollPane scrollPaneTransPrin;
	private JScrollPane scrollPaneTransFour;
	private JTable jTableFour = null;
	private JTable jTableTransPrin = null;
	private JTable jTableTransFour = null;
	// Obeservateur associé
	private AgentObservateur myAgent;
	// Liste d'information fournisseur
	private String[] columnNamesFour = { "Nom fournisseur", "Nombre Clients",
			"CA", "Bénéfice", "prixVente(unitaire)", "prixAchat(unitaire)" };
	// Liste d'information transporteur principal
	private String[] columnNamesTransPrin = { "Transporteur Principal",
			"Nombre Clients", "CA", "Bénéfice", "prixTransport(unitaire)" };
	// Liste d'information fournisseur
	private String[] columnNamesTransFour = { "Transporteur Fournisseur",
			"Nombre Clients", "CA", "Bénéfice", "prixTransport(par tranche)",
			"capacitéTransport" };

	/**
	 * This is the default constructor
	 */
	public AgentObservateurGUI(AgentObservateur a) {
		super(a.getLocalName());
		myAgent = a;
		initialize();
	}

	/**
	 * This method initializes this
	 * 
	 * @return void
	 */
	private void initialize() {
		this.setSize(400, 600);
		this.setContentPane(getJContentPane());
		this.setTitle("JFrame");
		this.scrollPaneFour = new JScrollPane(getJTableFour());
		this.jContentPane.add(scrollPaneFour);
		this.scrollPaneTransPrin = new JScrollPane(getJTableTransPrin());
		this.jContentPane.add(scrollPaneTransPrin);
		this.scrollPaneTransFour = new JScrollPane(getJTableTransFour());
		this.jContentPane.add(scrollPaneTransFour);

	}

	/**
	 * This method initializes jContentPane
	 * 
	 * @return javax.swing.JPanel
	 */
	private JPanel getJContentPane() {
		if (jContentPane == null) {
			jContentPane = new JPanel();
			jContentPane.setLayout(new GridLayout(2, 2));
		}
		return jContentPane;
	}

	/**
	 * This method initializes jTable
	 * 
	 * @return javax.swing.JTable
	 */
	private JTable getJTableFour() {
		if (jTableFour == null) {
			jTableFour = new JTable();
			DefaultTableModel contactTableModel = (DefaultTableModel) jTableFour
					.getModel();
			contactTableModel.setColumnIdentifiers(this.columnNamesFour);
		}
		return jTableFour;
	}

	private JTable getJTableTransPrin() {
		if (jTableTransPrin == null) {
			jTableTransPrin = new JTable();
			DefaultTableModel contactTableModel = (DefaultTableModel) jTableTransPrin
					.getModel();
			contactTableModel.setColumnIdentifiers(this.columnNamesTransPrin);
		}
		return jTableTransPrin;
	}

	private JTable getJTableTransFour() {
		if (jTableTransFour == null) {
			jTableTransFour = new JTable();
			DefaultTableModel contactTableModel = (DefaultTableModel) jTableTransFour
					.getModel();
			contactTableModel.setColumnIdentifiers(this.columnNamesTransFour);
		}
		return jTableTransFour;
	}

	public void reload(Object[][] dataFour, Object[][] dataTransPrin,
			Object[][] dataTransFour, int nbTour) {

		DefaultTableModel tableModelFour = (DefaultTableModel) jTableFour
				.getModel();
		tableModelFour.setRowCount(0);
		DefaultTableModel tableModelTransPrin = (DefaultTableModel) jTableTransPrin
				.getModel();
		tableModelTransPrin.setRowCount(0);
		DefaultTableModel tableModelTransFour = (DefaultTableModel) jTableTransFour
				.getModel();
		tableModelTransFour.setRowCount(0);
		// Comparateur pour trier la talbe par le nom de pour avoir une
		// affichage
		// "stable"
		Comparator<Object[]> comp = new Comparator<Object[]>() {
			public int compare(final Object[] entry1, final Object[] entry2) {
				final String time1 = entry1[0].toString();
				final String time2 = entry2[0].toString();
				return time1.compareTo(time2);
			}
		};
		Arrays.sort(dataFour, comp);
		for (int j = 0; j < dataFour.length; j++) {
			tableModelFour.addRow(dataFour[j]);
		}
		jTableFour.setModel(tableModelFour);
		jTableFour.repaint();

		Arrays.sort(dataTransPrin, comp);
		for (int j = 0; j < dataTransPrin.length; j++) {
			tableModelTransPrin.addRow(dataTransPrin[j]);
		}
		jTableTransPrin.setModel(tableModelTransPrin);
		jTableTransPrin.repaint();

		Arrays.sort(dataTransFour, comp);
		for (int j = 0; j < dataTransFour.length; j++) {
			tableModelTransFour.addRow(dataTransFour[j]);
		}
		jTableTransFour.setModel(tableModelTransFour);
		this.setTitle("nombre de tour: " + nbTour);
		jTableTransFour.repaint();

	}

	public void showGui() {
		pack();
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		int centerX = (int) screenSize.getWidth() / 2;
		int centerY = (int) screenSize.getHeight() / 2;
		setLocation(centerX - getWidth() / 2, centerY - getHeight() / 2);
		super.setVisible(true);
	}

}
