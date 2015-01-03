package ca.mcgill.mcb.pcingola.snpEffect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import ca.mcgill.mcb.pcingola.codons.CodonTable;
import ca.mcgill.mcb.pcingola.interval.Custom;
import ca.mcgill.mcb.pcingola.interval.Exon;
import ca.mcgill.mcb.pcingola.interval.Gene;
import ca.mcgill.mcb.pcingola.interval.Intron;
import ca.mcgill.mcb.pcingola.interval.Marker;
import ca.mcgill.mcb.pcingola.interval.Motif;
import ca.mcgill.mcb.pcingola.interval.NextProt;
import ca.mcgill.mcb.pcingola.interval.Regulation;
import ca.mcgill.mcb.pcingola.interval.Transcript;
import ca.mcgill.mcb.pcingola.interval.Variant;
import ca.mcgill.mcb.pcingola.vcf.VcfEffect;

/**
 * Effect of a variant.
 *
 * @author pcingola
 */
public class VariantEffect implements Cloneable, Comparable<VariantEffect> {

	public enum Coding {
		CODING, NON_CODING
	}

	public enum EffectImpact {
		HIGH, MODERATE, LOW, MODIFIER
	}

	/**
	 * Errors for change effect
	 *
	 */
	public enum ErrorWarningType {
		INFO_REALIGN_3_PRIME // 		Variant has been realigned to the most 3-prime position within the transcript. This is usually done to to comply with HGVS specification to always report the most 3-prime annotation.
		, WARNING_SEQUENCE_NOT_AVAILABLE // The exon does not have reference sequence information
		, WARNING_REF_DOES_NOT_MATCH_GENOME // Sequence reference does not match variant's reference (alignment problem?)
		, WARNING_TRANSCRIPT_INCOMPLETE // Number of coding bases is NOT multiple of 3, so there is missing information for at least one codon.
		, WARNING_TRANSCRIPT_MULTIPLE_STOP_CODONS // Multiple STOP codons found in a CDS (usually indicates frame errors un one or more exons)
		, WARNING_TRANSCRIPT_NO_START_CODON // Start codon does not match any 'start' codon in the CodonTable
		, WARNING_TRANSCRIPT_NO_STOP_CODON // Stop codon does not match any 'stop' codon in the CodonTable
		, ERROR_CHROMOSOME_NOT_FOUND // Chromosome name not found. Typically due to mismatch in chromosome naming conventions between variants file and database, but can be a more severa problem (different reference genome)
		, ERROR_OUT_OF_CHROMOSOME_RANGE // Variant is outside chromosome
		, ERROR_OUT_OF_EXON //
		, ERROR_MISSING_CDS_SEQUENCE // Missing coding sequence information
		;

		public boolean isError() {
			return toString().startsWith("ERROR");
		}

		public boolean isWarning() {
			return toString().startsWith("WARNING");
		}
	}

	/**
	 * This class is only getFused for SNPs
	 */
	public enum FunctionalClass {
		NONE, SILENT, MISSENSE, NONSENSE
	}

	// Don't show codon change sequences that are too long
	public static final int MAX_CODON_SEQUENCE_LEN = 100;

	Variant variant = null;
	List<EffectType> effectTypes;
	EffectType effectType;
	List<EffectImpact> effectImpacts;
	EffectImpact effectImpact = null;
	Marker marker = null;
	String error = "", warning = "", message = ""; // Any message, warning or error?
	String codonsRef = "", codonsAlt = ""; // Codon change information
	String codonsAroundOld = "", codonsAroundNew = ""; // Codons around
	int distance = -1; // Distance metric
	int cDnaPos = -1; // Position in cDNA
	int codonNum = -1; // Codon number (negative number mens 'information not available')
	int codonIndex = -1; // Index within a codon (negative number mens 'information not available')
	int codonDegeneracy = -1; // Codon degeneracy (negative number mens 'information not available')
	String aaRef = "", aaAlt = ""; // Amino acid changes
	String aasAroundOld = "", aasAroundNew = ""; // Amino acids around

	public VariantEffect(Variant variant) {
		this.variant = variant;
		effectTypes = new ArrayList<EffectType>();
		effectImpacts = new ArrayList<EffectImpact>();
	}

	public VariantEffect(Variant variant, Marker marker, EffectType effectType, EffectImpact effectImpact, String message, String codonsOld, String codonsNew, int codonNum, int codonIndex, int cDnaPos) {
		this.variant = variant;
		effectTypes = new ArrayList<EffectType>();
		effectImpacts = new ArrayList<EffectImpact>();
		set(marker, effectType, effectImpact, message);
		setCodons(codonsOld, codonsNew, codonNum, codonIndex);
		this.cDnaPos = cDnaPos;
	}

	public void addEffect(EffectType effectType) {
		addEffectType(effectType);
		addEffectImpact(effectType.effectImpact());
	}

	public void addEffectImpact(EffectImpact effectImpact) {
		effectImpacts.add(effectImpact);
		this.effectImpact = null;
	}

	public void addEffectType(EffectType effectType) {
		effectTypes.add(effectType);
		this.effectType = null;
	}

	public void addErrorMessage(ErrorWarningType errmsg) {
		addErrorWarningInfo(errmsg);
	}

	/**
	 * Add an error or warning
	 */
	public void addErrorWarningInfo(ErrorWarningType errwarn) {
		if (errwarn == null) return;

		if (errwarn.isError()) {
			if (error.indexOf(errwarn.toString()) < 0) error += (error.isEmpty() ? "" : VcfEffect.EFFECT_TYPE_SEPARATOR) + errwarn;
		} else {
			if (warning.indexOf(errwarn.toString()) < 0) warning += (warning.isEmpty() ? "" : VcfEffect.EFFECT_TYPE_SEPARATOR) + errwarn;
		}
	}

	public void addInfoMessage(ErrorWarningType infomsg) {
		addErrorWarningInfo(infomsg);
	}

	public void addWarningMessge(ErrorWarningType warnmsg) {
		addErrorWarningInfo(warnmsg);
	}

	@Override
	public VariantEffect clone() {
		try {
			return (VariantEffect) super.clone();
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Create a string for codon effect
	 * @param showAaChange : If true, include codon change, biotype, etc.
	 */
	String codonEffect(boolean showAaChange, boolean showBioType, boolean useSeqOntology) {
		if ((marker == null) || (codonNum < 0)) return "";

		if (!showAaChange) return getEffectTypeString(useSeqOntology);

		StringBuilder sb = new StringBuilder();
		sb.append(getEffectTypeString(useSeqOntology));
		sb.append("(");
		sb.append(getAaChange());
		sb.append(")");

		return sb.toString();
	}

	@Override
	public int compareTo(VariantEffect varEffOther) {
		// Sort by impact
		int comp = getEffectImpact().compareTo(varEffOther.getEffectImpact());
		if (comp != 0) return comp;

		// Sort by effect
		comp = getEffectType().compareTo(varEffOther.getEffectType());
		if (comp != 0) return comp;

		// Sort by: Is canonical transcript?
		Transcript trThis = getTranscript();
		Transcript trOther = varEffOther.getTranscript();
		if (trThis != null && trOther != null) {
			comp = (trOther.isCanonical() ? 1 : 0) - (trThis.isCanonical() ? 1 : 0);
		}
		if (comp != 0) return comp;

		// Sort by genomic coordinate of affected 'marker'
		if ((trThis != null) && (trOther != null)) comp = trThis.compareToPos(trOther);
		if (comp != 0) return comp;

		// Compare IDs
		if ((trThis != null) && (trOther != null)) comp = trThis.getId().compareTo(trOther.getId());
		if (comp != 0) return comp;

		// Compare by marker
		if ((getMarker() != null) && (varEffOther.getMarker() != null)) comp = getMarker().compareToPos(varEffOther.getMarker());
		if (comp != 0) return comp;

		// Sort by variant (most of the time this is equal)
		return variant.compareTo(varEffOther.getVariant());
	}

	/**
	 * Show a string with overall effect
	 */
	public String effect(boolean shortFormat, boolean showAaChange, boolean showBioType, boolean useSeqOntology) {
		String e = "";
		String codonEffect = codonEffect(showAaChange, showBioType, useSeqOntology); // Codon effect

		// Create effect string
		if (!codonEffect.isEmpty()) e = codonEffect;
		else if (isRegulation()) return getEffectTypeString(useSeqOntology) + "[" + ((Regulation) marker).getName() + "]";
		else if (isNextProt()) return getEffectTypeString(useSeqOntology) + "[" + VcfEffect.vcfEffSafe(((NextProt) marker).getId()) + "]"; // Make sure this 'id' is not dangerous in a VCF 'EFF' field
		else if (isMotif()) return getEffectTypeString(useSeqOntology) + "[" + ((Motif) marker).getPwmId() + ":" + ((Motif) marker).getPwmName() + "]";
		else if (isCustom()) {
			// Custom interval
			String label = ((Custom) marker).getLabel();
			double score = ((Custom) marker).getScore();
			if (!Double.isNaN(score)) label = label + ":" + score;
			if (!label.isEmpty()) label = "[" + label + "]";
			return getEffectTypeString(useSeqOntology) + label;
		} else if (isIntergenic() || isIntron() || isSpliceSite()) e = getEffectTypeString(useSeqOntology);
		else if (!message.isEmpty()) e = getEffectTypeString(useSeqOntology) + ": " + message;
		else if (marker == null) e = getEffectTypeString(useSeqOntology); // There are cases when no marker is associated (e.g. "Out of chromosome", "No such chromosome", etc.)
		else e = getEffectTypeString(useSeqOntology) + ": " + marker.getId();

		if (shortFormat) e = e.split(":")[0];

		return e;
	}

	public String getAaAlt() {
		return aaAlt;
	}

	/**
	 * Amino acid change string (HGVS style)
	 */
	public String getAaChange() {
		if (aaRef.isEmpty() && aaAlt.isEmpty()) {
			if (codonNum >= 0) return "" + (codonNum + 1);
			return "";
		}

		if (aaRef.equals(aaAlt)) return aaAlt + (codonNum + 1);
		return aaRef + (codonNum + 1) + aaAlt;
	}

	/**
	 * Amino acid change string (old style)
	 */
	public String getAaChangeOld() {
		if (aaRef.isEmpty() && aaAlt.isEmpty()) return "";
		if (aaRef.equals(aaAlt)) return aaAlt;
		return (aaRef.isEmpty() ? "-" : aaRef) + "/" + (aaAlt.isEmpty() ? "-" : aaAlt);
	}

	/**
	 * Amino acid length (negative if there is none)
	 * @return Amino acid length (CDS length / 3 ) or '-1' if there is no CDS length
	 */
	public int getAaLength() {
		int cdsLen = getCdsLength();
		if (cdsLen < 0) return -1;

		int lenNoStop = Math.max(0, cdsLen - 3); // Do not include the STOP codon
		return lenNoStop / 3;
	}

	/**
	 * Net AA change (only for InDels)
	 */
	public String getAaNetChange() {
		String aaLong = "", aaShort = "";

		if (variant.isIns()) {
			aaShort = getAaRef().toUpperCase();
			aaLong = getAaAlt().toUpperCase();
		} else if (variant.isDel()) {
			aaLong = getAaRef().toUpperCase();
			aaShort = getAaAlt().toUpperCase();
		}

		if (aaLong.startsWith(aaShort)) return aaLong.substring(aaShort.length());
		if (aaLong.endsWith(aaLong)) return aaLong.substring(0, aaLong.length() - aaShort.length());

		return aaLong;
	}

	public String getAaRef() {
		return aaRef;
	}

	/**
	 * Get biotype
	 */
	public String getBiotype() {
		Gene gene = getGene();
		if (gene == null) return "";

		Transcript tr = getTranscript();
		if (tr != null) return tr.getBioType();
		else if (gene.getGenome().hasCodingInfo()) return (gene.isProteinCoding() ? "coding" : "non-coding");

		return "";
	}

	public int getcDnaPos() {
		return cDnaPos;
	}

	/**
	 * CDS length (negative if there is none)
	 */
	public int getCdsLength() {
		// CDS size info
		Transcript tr = getTranscript();
		if ((tr != null) && tr.isProteinCoding()) return tr.cds().length();
		return -1;
	}

	/**
	 * Codon change string
	 */
	public String getCodonChange() {
		if (codonsRef.isEmpty() && codonsAlt.isEmpty()) return "";
		return codonsRef + "/" + codonsAlt;
	}

	/**
	 * Codon change string (if it's not too long)
	 */
	public String getCodonChangeMax() {
		if (variant.size() > MAX_CODON_SEQUENCE_LEN) return ""; // Cap length in order not to make VCF files grow too much
		if (codonsRef.isEmpty() && codonsAlt.isEmpty()) return "";
		return codonsRef + "/" + codonsAlt;
	}

	public int getCodonIndex() {
		return codonIndex;
	}

	public int getCodonNum() {
		return codonNum;
	}

	public String getCodonsAlt() {
		return codonsAlt;
	}

	public String getCodonsRef() {
		return codonsRef;
	}

	public int getDistance() {
		return distance;
	}

	/**
	 * Return impact of this effect
	 */
	public EffectImpact getEffectImpact() {
		if (effectImpact == null) {
			if ((variant != null) && (!variant.isVariant())) {
				// Not a change? => Modifier
				effectImpact = EffectImpact.MODIFIER;
			} else {
				// Get efefct's type highest impact
				effectImpact = EffectImpact.MODIFIER;
				for (EffectImpact eimp : effectImpacts)
					if (eimp.compareTo(effectImpact) < 0) effectImpact = eimp;
			}
		}

		return effectImpact;
	}

	/**
	 * Highest effect type
	 */
	public EffectType getEffectType() {
		if (effectType != null) return effectType;
		if (effectTypes == null || effectTypes.isEmpty()) return EffectType.NONE;

		// Pick highest effect type
		effectType = EffectType.NONE;
		for (EffectType et : effectTypes)
			if (et.compareTo(effectType) < 0) effectType = et;

		return effectType;
	}

	/**
	 * Highest effect type
	 */
	public List<EffectType> getEffectTypes() {
		return effectTypes;
	}

	public String getEffectTypeString(boolean useSeqOntology) {
		return getEffectTypeString(useSeqOntology, VcfEffect.EFFECT_TYPE_SEPARATOR_OLD);
	}

	/**
	 * Get Effect Type as a string
	 */
	public String getEffectTypeString(boolean useSeqOntology, String separator) {
		if (effectTypes == null) return "";

		// Show all effects
		StringBuilder sb = new StringBuilder();
		Collections.sort(effectTypes);
		for (EffectType et : effectTypes) {
			if (sb.length() > 0) sb.append(separator);
			if (useSeqOntology) sb.append(et.toSequenceOntology());
			else sb.append(et.toString());
		}

		return sb.toString();
	}

	public String getError() {
		return error;
	}

	/**
	 * Get exon (if any)
	 */
	public Exon getExon() {
		if (marker != null) {
			if (marker instanceof Exon) return (Exon) marker;
			return (Exon) marker.findParent(Exon.class);
		}
		return null;
	}

	/**
	 * Return functional class of this effect (i.e.  NONSENSE, MISSENSE, SILENT or NONE)
	 */
	public FunctionalClass getFunctionalClass() {
		if (variant.isSnp()) {
			if (!aaAlt.equals(aaRef)) {
				CodonTable codonTable = marker.codonTable();
				if (codonTable.isStop(codonsAlt)) return FunctionalClass.NONSENSE;

				return FunctionalClass.MISSENSE;
			}
			if (!codonsAlt.equals(codonsRef)) return FunctionalClass.SILENT;
		}

		return FunctionalClass.NONE;
	}

	public Gene getGene() {
		if (marker != null) {
			if (marker instanceof Gene) return (Gene) marker;
			return (Gene) marker.findParent(Gene.class);
		}
		return null;
	}

	public String getGeneRegion() {
		EffectType eff = getEffectType().getGeneRegion();
		if (eff == EffectType.TRANSCRIPT) {
			if (isExon()) eff = EffectType.TRANSCRIPT;
			else eff = EffectType.NONE;
		}

		return eff.toString();
	}

	/**
	 * Get genotype string
	 */
	public String getGenotype() {
		if (variant == null) return "";
		return variant.getGenotype();
	}

	/**
	 * Change in HGVS notation
	 */
	public String getHgvs() {
		// Calculate protein level and dna level changes
		String hgvsProt = getHgvsProt();
		String hgvsDna = getHgvsDna();

		// Build output
		StringBuilder hgsv = new StringBuilder();
		if (hgvsProt != null) hgsv.append(hgvsProt);
		if (hgvsDna != null) {
			if (hgsv.length() > 0) hgsv.append('/');
			hgsv.append(hgvsDna);
		}

		return hgsv.toString();
	}

	/**
	 * Change in HGVS (Dna) notation
	 */
	public String getHgvsDna() {
		HgvsDna hgvsDna = new HgvsDna(this);
		String hgvs = hgvsDna.toString();
		return hgvs != null ? hgvs : "";
	}

	/**
	 * Change in HGVS (Protein) notation
	 */
	public String getHgvsProt() {
		HgvsProtein hgvsProtein = new HgvsProtein(this);
		String hgvs = hgvsProtein.toString();
		return hgvs != null ? hgvs : "";
	}

	/**
	 * Get intron (if any)
	 */
	public Intron getIntron() {
		if (marker != null) {
			if (marker instanceof Intron) return (Intron) marker;
			return (Intron) marker.findParent(Intron.class);
		}
		return null;
	}

	public Marker getMarker() {
		return marker;
	}

	public Transcript getTranscript() {
		if (marker != null) {
			if (marker instanceof Transcript) return (Transcript) marker;
			return (Transcript) marker.findParent(Transcript.class);
		}
		return null;
	}

	public Variant getVariant() {
		return variant;
	}

	public String getWarning() {
		return warning;
	}

	/**
	 * Do we have an associated marker with additional annotations?
	 */
	public boolean hasAdditionalAnnotations() {
		return getMarker() != null // Do we have a marker?
				&& (getMarker() instanceof Custom) // Is it 'custom'?
				&& ((Custom) getMarker()).hasAnnotations() // Does it have additional annotations?
		;
	}

	public boolean hasEffectType(EffectType effectType) {
		for (EffectType effType : effectTypes)
			if (effType == effectType) return true;
		return false;
	}

	public boolean hasError() {
		return (error != null) && (!error.isEmpty());
	}

	public boolean hasWarning() {
		return (warning != null) && (!warning.isEmpty());
	}

	public boolean isCustom() {
		return getEffectType() == EffectType.CUSTOM;
	}

	public boolean isExon() {
		return (marker instanceof Exon) || hasEffectType(EffectType.EXON_DELETED);
	}

	public boolean isIntergenic() {
		return hasEffectType(EffectType.INTERGENIC) || hasEffectType(EffectType.INTERGENIC_CONSERVED);
	}

	public boolean isIntron() {
		return hasEffectType(EffectType.INTRON) || hasEffectType(EffectType.INTRON_CONSERVED);
	}

	public boolean isMotif() {
		return hasEffectType(EffectType.MOTIF);
	}

	public boolean isNextProt() {
		return hasEffectType(EffectType.NEXT_PROT);
	}

	public boolean isRegulation() {
		return hasEffectType(EffectType.REGULATION);
	}

	public boolean isSpliceSite() {
		return hasEffectType(EffectType.SPLICE_SITE_DONOR) //
				|| hasEffectType(EffectType.SPLICE_SITE_ACCEPTOR) //
				|| hasEffectType(EffectType.SPLICE_SITE_REGION) //
				|| hasEffectType(EffectType.SPLICE_SITE_BRANCH) //
				|| hasEffectType(EffectType.SPLICE_SITE_BRANCH_U12) //
		;
	}

	public boolean isSpliceSiteCore() {
		return hasEffectType(EffectType.SPLICE_SITE_DONOR) //
				|| hasEffectType(EffectType.SPLICE_SITE_ACCEPTOR) //
		;
	}

	public boolean isSpliceSiteRegion() {
		return hasEffectType(EffectType.SPLICE_SITE_REGION);
	}

	public boolean isUtr3() {
		return hasEffectType(EffectType.UTR_3_PRIME) || hasEffectType(EffectType.UTR_3_DELETED);
	}

	public boolean isUtr5() {
		return hasEffectType(EffectType.UTR_5_PRIME) || hasEffectType(EffectType.UTR_5_DELETED);
	}

	public void set(Marker marker, EffectType effectType, EffectImpact effectImpact, String message) {
		setMarker(marker); // Use setter because it takes care of warnings
		setEffectType(effectType);
		setEffectImpact(effectImpact);
		this.message = message;
	}

	/**
	 * Set codon change. Calculate effect type based on codon changes (for SNPs & MNPs)
	 */
	public void setCodons(String codonsOld, String codonsNew, int codonNum, int codonIndex) {
		codonsRef = codonsOld;
		codonsAlt = codonsNew;
		this.codonNum = codonNum;
		this.codonIndex = codonIndex;

		CodonTable codonTable = marker.codonTable();

		// Calculate amino acids
		if (codonsOld.isEmpty()) aaRef = "";
		else {
			aaRef = codonTable.aa(codonsOld);
			codonDegeneracy = codonTable.degenerate(codonsOld, codonIndex); // Calculate codon degeneracy
		}

		if (codonsNew.isEmpty()) aaAlt = "";
		else aaAlt = codonTable.aa(codonsNew);
	}

	/**
	 * Set values for codons around change.
	 */
	public void setCodonsAround(String codonsLeft, String codonsRight) {
		codonsAroundOld = codonsLeft.toLowerCase() + codonsRef.toUpperCase() + codonsRight.toLowerCase();
		codonsAroundNew = codonsLeft.toLowerCase() + codonsAlt.toUpperCase() + codonsRight.toLowerCase();

		// Amino acids surrounding the ones changed
		CodonTable codonTable = marker.codonTable();
		String aasLeft = codonTable.aa(codonsLeft);
		String aasRigt = codonTable.aa(codonsRight);
		aasAroundOld = aasLeft.toLowerCase() + aaRef.toUpperCase() + aasRigt.toLowerCase();
		aasAroundNew = aasLeft.toLowerCase() + aaAlt.toUpperCase() + aasRigt.toLowerCase();
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	/**
	 * Set effect using default impact
	 */
	public void setEffect(EffectType effectType) {
		setEffectType(effectType);
		setEffectImpact(effectType.effectImpact());
	}

	public void setEffectImpact(EffectImpact effectImpact) {
		effectImpacts.clear();
		effectImpacts.add(effectImpact);
		this.effectImpact = null;
	}

	public void setEffectType(EffectType effectType) {
		effectTypes.clear();
		effectTypes.add(effectType);
		this.effectType = null;
	}

	/**
	 * Set marker. Add some warnings if the marker relates to incomplete transcripts
	 */
	public void setMarker(Marker marker) {
		this.marker = marker;

		Transcript transcript = getTranscript();
		if (transcript != null) {
			// Transcript level errors or warnings
			addErrorWarningInfo(transcript.sanityCheck(variant));

			// Exon level errors or warnings
			Exon exon = getExon();
			if (exon != null) addErrorWarningInfo(exon.sanityCheck(variant));
		}
	}

	@Override
	public String toString() {
		return toString(false, false);
	}

	public String toString(boolean useSeqOntology, boolean useHgvs) {
		// Get data to show
		String geneId = "", geneName = "", bioType = "", transcriptId = "", exonId = "", customId = "";
		int exonRank = -1;

		if (marker != null) {
			// Gene Id, name and biotype
			Gene gene = getGene();
			Transcript tr = getTranscript();

			// CDS size info
			if (gene != null) {
				geneId = gene.getId();
				geneName = gene.getGeneName();
				bioType = getBiotype();
			}

			// Update trId
			if (tr != null) transcriptId = tr.getId();

			// Exon rank information
			Exon exon = getExon();
			if (exon != null) {
				exonId = exon.getId();
				exonRank = exon.getRank();
			}

			// Regulation
			if (isRegulation()) bioType = ((Regulation) marker).getCellType();
		}

		// Add seqChage's ID
		if (!variant.getId().isEmpty()) customId += variant.getId();

		// Add custom markers
		if ((marker != null) && (marker instanceof Custom)) customId += (customId.isEmpty() ? "" : ";") + marker.getId();

		// CDS length
		int cdsSize = getCdsLength();

		String errWarn = error + (error.isEmpty() ? "" : "|") + warning;

		String aaChange = "";
		if (useHgvs) aaChange = getHgvs();
		else aaChange = ((aaRef.length() + aaAlt.length()) > 0 ? aaRef + "/" + aaAlt : "");

		return errWarn //
				+ "\t" + geneId //
				+ "\t" + geneName //
				+ "\t" + bioType //
				+ "\t" + transcriptId //
				+ "\t" + exonId //
				+ "\t" + (exonRank >= 0 ? exonRank : "") //
				+ "\t" + effect(false, false, false, useSeqOntology) //
				+ "\t" + aaChange //
				+ "\t" + ((codonsRef.length() + codonsAlt.length()) > 0 ? codonsRef + "/" + codonsAlt : "") //
				+ "\t" + (codonNum >= 0 ? (codonNum + 1) : "") //
				+ "\t" + (codonDegeneracy >= 0 ? codonDegeneracy + "" : "") //
				+ "\t" + (cdsSize >= 0 ? cdsSize : "") //
				+ "\t" + (codonsAroundOld.length() > 0 ? codonsAroundOld + " / " + codonsAroundNew : "") //
				+ "\t" + (aasAroundOld.length() > 0 ? aasAroundOld + " / " + aasAroundNew : "") //
				+ "\t" + customId //
		;
	}

	/**
	 * Get the simplest string describing the effect (this is mostly used for testcases)
	 */
	public String toStringSimple(boolean shortFormat) {
		String transcriptId = "";
		Transcript tr = getTranscript();
		if (tr != null) transcriptId = tr.getId();

		String exonId = "";
		Exon exon = getExon();
		if (exon != null) exonId = exon.getId();

		String eff = effect(shortFormat, true, true, false);
		if (!eff.isEmpty()) return eff;
		if (!exonId.isEmpty()) return exonId;
		if (!transcriptId.isEmpty()) return transcriptId;

		return "NO EFFECT";
	}

	/**
	 * Return a string safe enough to be used in a VCF file
	 */
	String vcfSafe(String str) {
		return str;
	}

}
