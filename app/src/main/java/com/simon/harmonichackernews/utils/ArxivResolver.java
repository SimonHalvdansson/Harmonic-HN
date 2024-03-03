package com.simon.harmonichackernews.utils;

import java.util.HashMap;
import java.util.Map;

public class ArxivResolver {

    private static final Map<String, String> ARXIV_SUBJECTS = new HashMap<>();

    static {
        // Computer Science
        ARXIV_SUBJECTS.put("cs.AI", "Artificial Intelligence");
        ARXIV_SUBJECTS.put("cs.AR", "Hardware Architecture");
        ARXIV_SUBJECTS.put("cs.CC", "Computational Complexity");
        ARXIV_SUBJECTS.put("cs.CE", "Computational Engineering, Finance, and Science");
        ARXIV_SUBJECTS.put("cs.CG", "Computational Geometry");
        ARXIV_SUBJECTS.put("cs.CL", "Computation and Language");
        ARXIV_SUBJECTS.put("cs.CR", "Cryptography and Security");
        ARXIV_SUBJECTS.put("cs.CV", "Computer Vision and Pattern Recognition");
        ARXIV_SUBJECTS.put("cs.CY", "Computers and Society");
        ARXIV_SUBJECTS.put("cs.DB", "Databases");
        ARXIV_SUBJECTS.put("cs.DC", "Distributed, Parallel, and Cluster Computing");
        ARXIV_SUBJECTS.put("cs.DL", "Digital Libraries");
        ARXIV_SUBJECTS.put("cs.DM", "Discrete Mathematics");
        ARXIV_SUBJECTS.put("cs.DS", "Data Structures and Algorithms");
        ARXIV_SUBJECTS.put("cs.ET", "Emerging Technologies");
        ARXIV_SUBJECTS.put("cs.FL", "Formal Languages and Automata Theory");
        ARXIV_SUBJECTS.put("cs.GL", "General Literature");
        ARXIV_SUBJECTS.put("cs.GR", "Graphics");
        ARXIV_SUBJECTS.put("cs.GT", "Computer Science and Game Theory");
        ARXIV_SUBJECTS.put("cs.HC", "Human-Computer Interaction");
        ARXIV_SUBJECTS.put("cs.IR", "Information Retrieval");
        ARXIV_SUBJECTS.put("cs.IT", "Information Theory");
        ARXIV_SUBJECTS.put("cs.LG", "Machine Learning");
        ARXIV_SUBJECTS.put("cs.LO", "Logic in Computer Science");
        ARXIV_SUBJECTS.put("cs.MA", "Multiagent Systems");
        ARXIV_SUBJECTS.put("cs.MM", "Multimedia");
        ARXIV_SUBJECTS.put("cs.MS", "Mathematical Software");
        ARXIV_SUBJECTS.put("cs.NA", "Numerical Analysis");
        ARXIV_SUBJECTS.put("cs.NE", "Neural and Evolutionary Computing");
        ARXIV_SUBJECTS.put("cs.NI", "Networking and Internet Architecture");
        ARXIV_SUBJECTS.put("cs.OH", "Other Computer Science");
        ARXIV_SUBJECTS.put("cs.OS", "Operating Systems");
        ARXIV_SUBJECTS.put("cs.PF", "Performance");
        ARXIV_SUBJECTS.put("cs.PL", "Programming Languages");
        ARXIV_SUBJECTS.put("cs.RO", "Robotics");
        ARXIV_SUBJECTS.put("cs.SC", "Symbolic Computation");
        ARXIV_SUBJECTS.put("cs.SD", "Sound");
        ARXIV_SUBJECTS.put("cs.SE", "Software Engineering");
        ARXIV_SUBJECTS.put("cs.SI", "Social and Information Networks");
        ARXIV_SUBJECTS.put("cs.SY", "Systems and Control");

        // Economics
        ARXIV_SUBJECTS.put("econ.EM", "Econometrics");
        ARXIV_SUBJECTS.put("econ.GN", "General Economics");
        ARXIV_SUBJECTS.put("econ.TH", "Theoretical Economics");

        // Electrical Engineering and Systems Science
        ARXIV_SUBJECTS.put("eess.AS", "Audio and Speech Processing");
        ARXIV_SUBJECTS.put("eess.IV", "Image and Video Processing");
        ARXIV_SUBJECTS.put("eess.SP", "Signal Processing");
        ARXIV_SUBJECTS.put("eess.SY", "Systems and Control");

        // Mathematics
        ARXIV_SUBJECTS.put("math.AC", "Commutative Algebra");
        ARXIV_SUBJECTS.put("math.AG", "Algebraic Geometry");
        ARXIV_SUBJECTS.put("math.AP", "Analysis of PDEs");
        ARXIV_SUBJECTS.put("math.AT", "Algebraic Topology");
        ARXIV_SUBJECTS.put("math.CA", "Classical Analysis and ODEs");
        ARXIV_SUBJECTS.put("math.CO", "Combinatorics");
        ARXIV_SUBJECTS.put("math.CT", "Category Theory");
        ARXIV_SUBJECTS.put("math.CV", "Complex Variables");
        ARXIV_SUBJECTS.put("math.DG", "Differential Geometry");
        ARXIV_SUBJECTS.put("math.DS", "Dynamical Systems");
        ARXIV_SUBJECTS.put("math.FA", "Functional Analysis");
        ARXIV_SUBJECTS.put("math.GM", "General Mathematics");
        ARXIV_SUBJECTS.put("math.GN", "General Topology");
        ARXIV_SUBJECTS.put("math.GR", "Group Theory");
        ARXIV_SUBJECTS.put("math.GT", "Geometric Topology");
        ARXIV_SUBJECTS.put("math.HO", "History and Overview");
        ARXIV_SUBJECTS.put("math.IT", "Information Theory");
        ARXIV_SUBJECTS.put("math.KT", "K-Theory and Homology");
        ARXIV_SUBJECTS.put("math.LO", "Logic");
        ARXIV_SUBJECTS.put("math.MG", "Metric Geometry");
        ARXIV_SUBJECTS.put("math.MP", "Mathematical Physics");
        ARXIV_SUBJECTS.put("math.NA", "Numerical Analysis");
        ARXIV_SUBJECTS.put("math.NT", "Number Theory");
        ARXIV_SUBJECTS.put("math.OA", "Operator Algebras");
        ARXIV_SUBJECTS.put("math.OC", "Optimization and Control");
        ARXIV_SUBJECTS.put("math.PR", "Probability");
        ARXIV_SUBJECTS.put("math.QA", "Quantum Algebra");
        ARXIV_SUBJECTS.put("math.RA", "Rings and Algebras");
        ARXIV_SUBJECTS.put("math.RT", "Representation Theory");
        ARXIV_SUBJECTS.put("math.SG", "Symplectic Geometry");
        ARXIV_SUBJECTS.put("math.SP", "Spectral Theory");
        ARXIV_SUBJECTS.put("math.ST", "Statistics Theory");

        // ASTROPHYSICS
        ARXIV_SUBJECTS.put("astro-ph.CO", "Cosmology and Nongalactic Astrophysics");
        ARXIV_SUBJECTS.put("astro-ph.EP", "Earth and Planetary Astrophysics");
        ARXIV_SUBJECTS.put("astro-ph.GA", "Astrophysics of Galaxies");
        ARXIV_SUBJECTS.put("astro-ph.HE", "High Energy Astrophysical Phenomena");
        ARXIV_SUBJECTS.put("astro-ph.IM", "Instrumentation and Methods for Astrophysics");
        ARXIV_SUBJECTS.put("astro-ph.SR", "Solar and Stellar Astrophysics");

        // CONDENSED MATTER
        ARXIV_SUBJECTS.put("cond-mat.dis-nn", "Disordered Systems and Neural Networks");
        ARXIV_SUBJECTS.put("cond-mat.mes-hall", "Mesoscale and Nanoscale Physics");
        ARXIV_SUBJECTS.put("cond-mat.mtrl-sci", "Materials Science");
        ARXIV_SUBJECTS.put("cond-mat.other", "Other Condensed Matter");
        ARXIV_SUBJECTS.put("cond-mat.quant-gas", "Quantum Gases");
        ARXIV_SUBJECTS.put("cond-mat.soft", "Soft Condensed Matter");
        ARXIV_SUBJECTS.put("cond-mat.stat-mech", "Statistical Mechanics");
        ARXIV_SUBJECTS.put("cond-mat.str-el", "Strongly Correlated Electrons");
        ARXIV_SUBJECTS.put("cond-mat.supr-con", "Superconductivity");

        // GENERAL RELATIVITY AND QUANTUM COSMOLOGY
        ARXIV_SUBJECTS.put("gr-qc", "General Relativity and Quantum Cosmology");

        // HIGH ENERGY PHYSICS
        ARXIV_SUBJECTS.put("hep-ex", "High Energy Physics - Experiment");
        ARXIV_SUBJECTS.put("hep-lat", "High Energy Physics - Lattice");
        ARXIV_SUBJECTS.put("hep-ph", "High Energy Physics - Phenomenology");
        ARXIV_SUBJECTS.put("hep-th", "High Energy Physics - Theory");

        // MATHEMATICAL PHYSICS
        ARXIV_SUBJECTS.put("math-ph", "Mathematical Physics");

        // NONLINEAR SCIENCES
        ARXIV_SUBJECTS.put("nlin.AO", "Adaptation and Self-Organizing Systems");
        ARXIV_SUBJECTS.put("nlin.CD", "Chaotic Dynamics");
        ARXIV_SUBJECTS.put("nlin.CG", "Cellular Automata and Lattice Gases");
        ARXIV_SUBJECTS.put("nlin.PS", "Pattern Formation and Solitons");
        ARXIV_SUBJECTS.put("nlin.SI", "Exactly Solvable and Integrable Systems");

        // NUCLEAR
        ARXIV_SUBJECTS.put("nucl-ex", "Nuclear Experiment");
        ARXIV_SUBJECTS.put("nucl-th", "Nuclear Theory");

        // PHYSICS
        ARXIV_SUBJECTS.put("physics.acc-ph", "Accelerator Physics");
        ARXIV_SUBJECTS.put("physics.ao-ph", "Atmospheric and Oceanic Physics");
        ARXIV_SUBJECTS.put("physics.app-ph", "Applied Physics");
        ARXIV_SUBJECTS.put("physics.atm-clus", "Atomic and Molecular Clusters");
        ARXIV_SUBJECTS.put("physics.atom-ph", "Atomic Physics");
        ARXIV_SUBJECTS.put("physics.bio-ph", "Biological Physics");
        ARXIV_SUBJECTS.put("physics.chem-ph", "Chemical Physics");
        ARXIV_SUBJECTS.put("physics.class-ph", "Classical Physics");
        ARXIV_SUBJECTS.put("physics.comp-ph", "Computational Physics");
        ARXIV_SUBJECTS.put("physics.data-an", "Data Analysis, Statistics and Probability");
        ARXIV_SUBJECTS.put("physics.ed-ph", "Physics Education");
        ARXIV_SUBJECTS.put("physics.flu-dyn", "Fluid Dynamics");
        ARXIV_SUBJECTS.put("physics.gen-ph", "General Physics");
        ARXIV_SUBJECTS.put("physics.geo-ph", "Geophysics");
        ARXIV_SUBJECTS.put("physics.hist-ph", "History and Philosophy of Physics");
        ARXIV_SUBJECTS.put("physics.ins-det", "Instrumentation and Detectors");
        ARXIV_SUBJECTS.put("physics.med-ph", "Medical Physics");
        ARXIV_SUBJECTS.put("physics.optics", "Optics");
        ARXIV_SUBJECTS.put("physics.plasm-ph", "Plasma Physics");
        ARXIV_SUBJECTS.put("physics.pop-ph", "Popular Physics");
        ARXIV_SUBJECTS.put("physics.soc-ph", "Physics and Society");
        ARXIV_SUBJECTS.put("physics.space-ph", "Space Physics");

        // QUANTUM PHYSICS
        ARXIV_SUBJECTS.put("quant-ph", "Quantum Physics");

        // Quantitative Biology
        ARXIV_SUBJECTS.put("q-bio.BM", "Biomolecules");
        ARXIV_SUBJECTS.put("q-bio.CB", "Cell Behavior");
        ARXIV_SUBJECTS.put("q-bio.GN", "Genomics");
        ARXIV_SUBJECTS.put("q-bio.MN", "Molecular Networks");
        ARXIV_SUBJECTS.put("q-bio.NC", "Neurons and Cognition");
        ARXIV_SUBJECTS.put("q-bio.OT", "Other Quantitative Biology");
        ARXIV_SUBJECTS.put("q-bio.PE", "Populations and Evolution");
        ARXIV_SUBJECTS.put("q-bio.QM", "Quantitative Methods");
        ARXIV_SUBJECTS.put("q-bio.SC", "Subcellular Processes");
        ARXIV_SUBJECTS.put("q-bio.TO", "Tissues and Organs");

        // Quantitative Finance
        ARXIV_SUBJECTS.put("q-fin.CP", "Computational Finance");
        ARXIV_SUBJECTS.put("q-fin.EC", "Economics");
        ARXIV_SUBJECTS.put("q-fin.GN", "General Finance");
        ARXIV_SUBJECTS.put("q-fin.MF", "Mathematical Finance");
        ARXIV_SUBJECTS.put("q-fin.PM", "Portfolio Management");
        ARXIV_SUBJECTS.put("q-fin.PR", "Pricing of Securities");
        ARXIV_SUBJECTS.put("q-fin.RM", "Risk Management");
        ARXIV_SUBJECTS.put("q-fin.ST", "Statistical Finance");
        ARXIV_SUBJECTS.put("q-fin.TR", "Trading and Market Microstructure");

        // Statistics
        ARXIV_SUBJECTS.put("stat.AP", "Applications");
        ARXIV_SUBJECTS.put("stat.CO", "Computation");
        ARXIV_SUBJECTS.put("stat.ME", "Methodology");
        ARXIV_SUBJECTS.put("stat.ML", "Machine Learning");
        ARXIV_SUBJECTS.put("stat.OT", "Other Statistics");
        ARXIV_SUBJECTS.put("stat.TH", "Statistics Theory");
    }

    /**
     * Resolves the arXiv subject abbreviation to its full name.
     *
     * @param abbreviation the arXiv subject abbreviation.
     * @return the full name of the subject, or null if the abbreviation is not recognized.
     */
    public static String resolveSubject(String abbreviation) {
        return ARXIV_SUBJECTS.get(abbreviation);
    }

    public static boolean isArxivSubjet(String abbr) {
        return ARXIV_SUBJECTS.containsKey(abbr);
    }

    public static String resolveFull(String category) {
        return ArxivResolver.resolveSubject(category) + " (" + category + ")";
    }

}