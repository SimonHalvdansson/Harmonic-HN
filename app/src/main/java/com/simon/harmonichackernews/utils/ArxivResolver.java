package com.simon.harmonichackernews.utils;

import java.util.HashMap;
import java.util.Map;

public class ArxivResolver {

    private static final Map<String, String> ARXIV_SUBJECTS = new HashMap<>();

    static {
        //Computer Science
        ARXIV_SUBJECTS.put("cs.AI", "Computer Science, Artificial Intelligence");
        ARXIV_SUBJECTS.put("cs.AR", "Computer Science, Hardware Architecture");
        ARXIV_SUBJECTS.put("cs.CC", "Computer Science, Computational Complexity");
        ARXIV_SUBJECTS.put("cs.CE", "Computer Science, Computational Engineering, Finance, and Science");
        ARXIV_SUBJECTS.put("cs.CG", "Computer Science, Computational Geometry");
        ARXIV_SUBJECTS.put("cs.CL", "Computer Science, Computation and Language");
        ARXIV_SUBJECTS.put("cs.CR", "Computer Science, Cryptography and Security");
        ARXIV_SUBJECTS.put("cs.CV", "Computer Science, Computer Vision and Pattern Recognition");
        ARXIV_SUBJECTS.put("cs.CY", "Computer Science, Computers and Society");
        ARXIV_SUBJECTS.put("cs.DB", "Computer Science, Databases");
        ARXIV_SUBJECTS.put("cs.DC", "Computer Science, Distributed, Parallel, and Cluster Computing");
        ARXIV_SUBJECTS.put("cs.DL", "Computer Science, Digital Libraries");
        ARXIV_SUBJECTS.put("cs.DM", "Computer Science, Discrete Mathematics");
        ARXIV_SUBJECTS.put("cs.DS", "Computer Science, Data Structures and Algorithms");
        ARXIV_SUBJECTS.put("cs.ET", "Computer Science, Emerging Technologies");
        ARXIV_SUBJECTS.put("cs.FL", "Computer Science, Formal Languages and Automata Theory");
        ARXIV_SUBJECTS.put("cs.GL", "Computer Science, General Literature");
        ARXIV_SUBJECTS.put("cs.GR", "Computer Science, Graphics");
        ARXIV_SUBJECTS.put("cs.GT", "Computer Science, Computer Science and Game Theory");
        ARXIV_SUBJECTS.put("cs.HC", "Computer Science, Human-Computer Interaction");
        ARXIV_SUBJECTS.put("cs.IR", "Computer Science, Information Retrieval");
        ARXIV_SUBJECTS.put("cs.IT", "Computer Science, Information Theory");
        ARXIV_SUBJECTS.put("cs.LG", "Computer Science, Machine Learning");
        ARXIV_SUBJECTS.put("cs.LO", "Computer Science, Logic in Computer Science");
        ARXIV_SUBJECTS.put("cs.MA", "Computer Science, Multiagent Systems");
        ARXIV_SUBJECTS.put("cs.MM", "Computer Science, Multimedia");
        ARXIV_SUBJECTS.put("cs.MS", "Computer Science, Mathematical Software");
        ARXIV_SUBJECTS.put("cs.NA", "Computer Science, Numerical Analysis");
        ARXIV_SUBJECTS.put("cs.NE", "Computer Science, Neural and Evolutionary Computing");
        ARXIV_SUBJECTS.put("cs.NI", "Computer Science, Networking and Internet Architecture");
        ARXIV_SUBJECTS.put("cs.OH", "Computer Science, Other Computer Science");
        ARXIV_SUBJECTS.put("cs.OS", "Computer Science, Operating Systems");
        ARXIV_SUBJECTS.put("cs.PF", "Computer Science, Performance");
        ARXIV_SUBJECTS.put("cs.PL", "Computer Science, Programming Languages");
        ARXIV_SUBJECTS.put("cs.RO", "Computer Science, Robotics");
        ARXIV_SUBJECTS.put("cs.SC", "Computer Science, Symbolic Computation");
        ARXIV_SUBJECTS.put("cs.SD", "Computer Science, Sound");
        ARXIV_SUBJECTS.put("cs.SE", "Computer Science, Software Engineering");
        ARXIV_SUBJECTS.put("cs.SI", "Computer Science, Social and Information Networks");
        ARXIV_SUBJECTS.put("cs.SY", "Computer Science, Systems and Control");

//Economics
        ARXIV_SUBJECTS.put("econ.EM", "Economics, Econometrics");
        ARXIV_SUBJECTS.put("econ.GN", "Economics, General Economics");
        ARXIV_SUBJECTS.put("econ.TH", "Economics, Theoretical Economics");

        //Electrical Engineering and Systems Science
        ARXIV_SUBJECTS.put("eess.AS", "Electrical Engineering and Systems Science, Audio and Speech Processing");
        ARXIV_SUBJECTS.put("eess.IV", "Electrical Engineering and Systems Science, Image and Video Processing");
        ARXIV_SUBJECTS.put("eess.SP", "Electrical Engineering and Systems Science, Signal Processing");
        ARXIV_SUBJECTS.put("eess.SY", "Electrical Engineering and Systems Science, Systems and Control");

//Mathematics
        ARXIV_SUBJECTS.put("math.AC", "Mathematics, Commutative Algebra");
        ARXIV_SUBJECTS.put("math.AG", "Mathematics, Algebraic Geometry");
        ARXIV_SUBJECTS.put("math.AP", "Mathematics, Analysis of PDEs");
        ARXIV_SUBJECTS.put("math.AT", "Mathematics, Algebraic Topology");
        ARXIV_SUBJECTS.put("math.CA", "Mathematics, Classical Analysis and ODEs");
        ARXIV_SUBJECTS.put("math.CO", "Mathematics, Combinatorics");
        ARXIV_SUBJECTS.put("math.CT", "Mathematics, Category Theory");
        ARXIV_SUBJECTS.put("math.CV", "Mathematics, Complex Variables");
        ARXIV_SUBJECTS.put("math.DG", "Mathematics, Differential Geometry");
        ARXIV_SUBJECTS.put("math.DS", "Mathematics, Dynamical Systems");
        ARXIV_SUBJECTS.put("math.FA", "Mathematics, Functional Analysis");
        ARXIV_SUBJECTS.put("math.GM", "Mathematics, General Mathematics");
        ARXIV_SUBJECTS.put("math.GN", "Mathematics, General Topology");
        ARXIV_SUBJECTS.put("math.GR", "Mathematics, Group Theory");
        ARXIV_SUBJECTS.put("math.GT", "Mathematics, Geometric Topology");
        ARXIV_SUBJECTS.put("math.HO", "Mathematics, History and Overview");
        ARXIV_SUBJECTS.put("math.IT", "Mathematics, Information Theory");
        ARXIV_SUBJECTS.put("math.KT", "Mathematics, K-Theory and Homology");
        ARXIV_SUBJECTS.put("math.LO", "Mathematics, Logic");
        ARXIV_SUBJECTS.put("math.MG", "Mathematics, Metric Geometry");
        ARXIV_SUBJECTS.put("math.MP", "Mathematics, Mathematical Physics");
        ARXIV_SUBJECTS.put("math.NA", "Mathematics, Numerical Analysis");
        ARXIV_SUBJECTS.put("math.NT", "Mathematics, Number Theory");
        ARXIV_SUBJECTS.put("math.OA", "Mathematics, Operator Algebras");
        ARXIV_SUBJECTS.put("math.OC", "Mathematics, Optimization and Control");
        ARXIV_SUBJECTS.put("math.PR", "Mathematics, Probability");
        ARXIV_SUBJECTS.put("math.QA", "Mathematics, Quantum Algebra");
        ARXIV_SUBJECTS.put("math.RA", "Mathematics, Rings and Algebras");
        ARXIV_SUBJECTS.put("math.RT", "Mathematics, Representation Theory");
        ARXIV_SUBJECTS.put("math.SG", "Mathematics, Symplectic Geometry");
        ARXIV_SUBJECTS.put("math.SP", "Mathematics, Spectral Theory");
        ARXIV_SUBJECTS.put("math.ST", "Mathematics, Statistics Theory");

//ASTROPHYSICS
        ARXIV_SUBJECTS.put("astro-ph.CO", "Astrophysics, Cosmology and Nongalactic Astrophysics");
        ARXIV_SUBJECTS.put("astro-ph.EP", "Astrophysics, Earth and Planetary Astrophysics");
        ARXIV_SUBJECTS.put("astro-ph.GA", "Astrophysics, Astrophysics of Galaxies");
        ARXIV_SUBJECTS.put("astro-ph.HE", "Astrophysics, High Energy Astrophysical Phenomena");
        ARXIV_SUBJECTS.put("astro-ph.IM", "Astrophysics, Instrumentation and Methods for Astrophysics");
        ARXIV_SUBJECTS.put("astro-ph.SR", "Astrophysics, Solar and Stellar Astrophysics");

//CONDENSED MATTER
        ARXIV_SUBJECTS.put("cond-mat.dis-nn", "Condensed Matter, Disordered Systems and Neural Networks");
        ARXIV_SUBJECTS.put("cond-mat.mes-hall", "Condensed Matter, Mesoscale and Nanoscale Physics");
        ARXIV_SUBJECTS.put("cond-mat.mtrl-sci", "Condensed Matter, Materials Science");
        ARXIV_SUBJECTS.put("cond-mat.other", "Condensed Matter, Other Condensed Matter");
        ARXIV_SUBJECTS.put("cond-mat.quant-gas", "Condensed Matter, Quantum Gases");
        ARXIV_SUBJECTS.put("cond-mat.soft", "Condensed Matter, Soft Condensed Matter");
        ARXIV_SUBJECTS.put("cond-mat.stat-mech", "Condensed Matter, Statistical Mechanics");
        ARXIV_SUBJECTS.put("cond-mat.str-el", "Condensed Matter, Strongly Correlated Electrons");
        ARXIV_SUBJECTS.put("cond-mat.supr-con", "Condensed Matter, Superconductivity");

//GENERAL RELATIVITY AND QUANTUM COSMOLOGY
        ARXIV_SUBJECTS.put("gr-qc", "General Relativity and Quantum Cosmology");

//HIGH ENERGY PHYSICS
        ARXIV_SUBJECTS.put("hep-ex", "High Energy Physics, Experiment");
        ARXIV_SUBJECTS.put("hep-lat", "High Energy Physics, Lattice");
        ARXIV_SUBJECTS.put("hep-ph", "High Energy Physics, Phenomenology");
        ARXIV_SUBJECTS.put("hep-th", "High Energy Physics, Theory");

//MATHEMATICAL PHYSICS
        ARXIV_SUBJECTS.put("math-ph", "Mathematical Physics");

//NONLINEAR SCIENCES
        ARXIV_SUBJECTS.put("nlin.AO", "Nonlinear Sciences, Adaptation and Self-Organizing Systems");
        ARXIV_SUBJECTS.put("nlin.CD", "Nonlinear Sciences, Chaotic Dynamics");
        ARXIV_SUBJECTS.put("nlin.CG", "Nonlinear Sciences, Cellular Automata and Lattice Gases");
        ARXIV_SUBJECTS.put("nlin.PS", "Nonlinear Sciences, Pattern Formation and Solitons");
        ARXIV_SUBJECTS.put("nlin.SI", "Nonlinear Sciences, Exactly Solvable and Integrable Systems");

//NUCLEAR
        ARXIV_SUBJECTS.put("nucl-ex", "Nuclear, Experiment");
        ARXIV_SUBJECTS.put("nucl-th", "Nuclear, Theory");

//PHYSICS
        ARXIV_SUBJECTS.put("physics.acc-ph", "Physics, Accelerator Physics");
        ARXIV_SUBJECTS.put("physics.ao-ph", "Physics, Atmospheric and Oceanic Physics");
        ARXIV_SUBJECTS.put("physics.app-ph", "Physics, Applied Physics");
        ARXIV_SUBJECTS.put("physics.atm-clus", "Physics, Atomic and Molecular Clusters");
        ARXIV_SUBJECTS.put("physics.atom-ph", "Physics, Atomic Physics");
        ARXIV_SUBJECTS.put("physics.bio-ph", "Physics, Biological Physics");
        ARXIV_SUBJECTS.put("physics.chem-ph", "Physics, Chemical Physics");
        ARXIV_SUBJECTS.put("physics.class-ph", "Physics, Classical Physics");
        ARXIV_SUBJECTS.put("physics.comp-ph", "Physics, Computational Physics");
        ARXIV_SUBJECTS.put("physics.data-an", "Physics, Data Analysis, Statistics and Probability");
        ARXIV_SUBJECTS.put("physics.ed-ph", "Physics, Physics Education");
        ARXIV_SUBJECTS.put("physics.flu-dyn", "Physics, Fluid Dynamics");
        ARXIV_SUBJECTS.put("physics.gen-ph", "Physics, General Physics");
        ARXIV_SUBJECTS.put("physics.geo-ph", "Physics, Geophysics");
        ARXIV_SUBJECTS.put("physics.hist-ph", "Physics, History and Philosophy of Physics");
        ARXIV_SUBJECTS.put("physics.ins-det", "Physics, Instrumentation and Detectors");
        ARXIV_SUBJECTS.put("physics.med-ph", "Physics, Medical Physics");
        ARXIV_SUBJECTS.put("physics.optics", "Physics, Optics");
        ARXIV_SUBJECTS.put("physics.plasm-ph", "Physics, Plasma Physics");
        ARXIV_SUBJECTS.put("physics.pop-ph", "Physics, Popular Physics");
        ARXIV_SUBJECTS.put("physics.soc-ph", "Physics, Physics and Society");
        ARXIV_SUBJECTS.put("physics.space-ph", "Physics, Space Physics");

//QUANTUM PHYSICS
        ARXIV_SUBJECTS.put("quant-ph", "Quantum Physics");

//Quantitative Biology
        ARXIV_SUBJECTS.put("q-bio.BM", "Quantitative Biology, Biomolecules");
        ARXIV_SUBJECTS.put("q-bio.CB", "Quantitative Biology, Cell Behavior");
        ARXIV_SUBJECTS.put("q-bio.GN", "Quantitative Biology, Genomics");
        ARXIV_SUBJECTS.put("q-bio.MN", "Quantitative Biology, Molecular Networks");
        ARXIV_SUBJECTS.put("q-bio.NC", "Quantitative Biology, Neurons and Cognition");
        ARXIV_SUBJECTS.put("q-bio.OT", "Quantitative Biology, Other Quantitative Biology");
        ARXIV_SUBJECTS.put("q-bio.PE", "Quantitative Biology, Populations and Evolution");
        ARXIV_SUBJECTS.put("q-bio.QM", "Quantitative Biology, Quantitative Methods");
        ARXIV_SUBJECTS.put("q-bio.SC", "Quantitative Biology, Subcellular Processes");
        ARXIV_SUBJECTS.put("q-bio.TO", "Quantitative Biology, Tissues and Organs");

//Quantitative Finance
        ARXIV_SUBJECTS.put("q-fin.CP", "Quantitative Finance, Computational Finance");
        ARXIV_SUBJECTS.put("q-fin.EC", "Quantitative Finance, Economics");
        ARXIV_SUBJECTS.put("q-fin.GN", "Quantitative Finance, General Finance");
        ARXIV_SUBJECTS.put("q-fin.MF", "Quantitative Finance, Mathematical Finance");
        ARXIV_SUBJECTS.put("q-fin.PM", "Quantitative Finance, Portfolio Management");
        ARXIV_SUBJECTS.put("q-fin.PR", "Quantitative Finance, Pricing of Securities");
        ARXIV_SUBJECTS.put("q-fin.RM", "Quantitative Finance, Risk Management");
        ARXIV_SUBJECTS.put("q-fin.ST", "Quantitative Finance, Statistical Finance");
        ARXIV_SUBJECTS.put("q-fin.TR", "Quantitative Finance, Trading and Market Microstructure");

//Statistics
        ARXIV_SUBJECTS.put("stat.AP", "Statistics, Applications");
        ARXIV_SUBJECTS.put("stat.CO", "Statistics, Computation");
        ARXIV_SUBJECTS.put("stat.ME", "Statistics, Methodology");
        ARXIV_SUBJECTS.put("stat.ML", "Statistics, Machine Learning");
        ARXIV_SUBJECTS.put("stat.OT", "Statistics, Other Statistics");
        ARXIV_SUBJECTS.put("stat.TH", "Statistics, Statistics Theory");
    }

    /**
     * Resolves the arXiv subject abbreviation to its full name.
     * @param abbreviation the arXiv subject abbreviation.
     * @return the full name of the subject, or null if the abbreviation is not recognized.
     */
    public static String resolveSubject(String abbreviation) {
        return ARXIV_SUBJECTS.get(abbreviation);
    }

    public static void main(String[] args) {
        String subject = "cs.AI";
        System.out.println(subject + " -> " + resolveSubject(subject));
    }
}