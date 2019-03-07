package nlp.parser;

import java.util.*;
import java.io.*;

// Represents a candidate parse. To be inserted into cells of DP table.
class Candidate implements Comparable<Candidate> {
  public String constituent;
  public double totalWeight;
  public Candidate leftChild;
  public Candidate rightChild;

  public Candidate (String s, double d, Candidate l, Candidate r) {
    constituent = s;
    totalWeight = d;
    leftChild = l;
    rightChild = r;
  }
  public Candidate (String s, double d, Candidate c) {
    this(s, d, c, null);
  }
  public Candidate (String s, double d) {
    this(s, d, null, null);
  }

  public String toStringRec (boolean originalTree) {

    if (leftChild == null) return constituent;
    // If unary rule
    if (rightChild == null) return "(" + constituent + " " + leftChild.toStringRec(originalTree) + ")";

    // Check if using a helper binary rule (ex. X23)
    String childrenStr = leftChild.toStringRec(originalTree) + " " + rightChild.toStringRec(originalTree);
    if (originalTree && constituent.charAt(0) == 'X') {
      return childrenStr;
    }
    return "(" + constituent + " " + childrenStr + ")";

  }

  public String toString (boolean originalTree) {
    return toStringRec(originalTree) + " " + totalWeight;
  }

  @Override
  public int compareTo (Candidate b) {
    Double aWeight = new Double(totalWeight);
    Double bWeight = new Double(b.totalWeight);
    return aWeight.compareTo(bWeight);
  }
}

public class CKY {

  private List<GrammarRule> lexicalRules, binaryRules;
  private Map<String,List<GrammarRule>> unaryRules; // rhs -> lhs
  private boolean originalTree;
  private int beamSize;

  public CKY (String grammarFile, String sentencesFile, boolean originalTree, int beamSize) {

    this.originalTree = originalTree;
    this.beamSize = beamSize;

    lexicalRules = new ArrayList<>();
    unaryRules = new HashMap<>();
    binaryRules = new ArrayList<>();

    // Read in rules from file
    buildRules(grammarFile);

    parseSentences(sentencesFile);

  }

  // Tries adding a candidate to a cell, returns true if successfil
  private boolean addCandidateToCell (HashMap<String, Candidate> cell, Candidate newCand) {
    String constituent = newCand.constituent;
    // Add if cell doesn't contain this constituent, or if newCand is better
    boolean addCell = !cell.containsKey(constituent) ||
      newCand.totalWeight > cell.get(constituent).totalWeight;

    if (addCell) {
      cell.put(constituent, newCand);
      addAllUnary(cell, newCand);
    }

    return addCell;
  }

  // Adds all the unary rules for a new parse that's just been added to DP table
  private void addAllUnary (HashMap<String,Candidate> cell, Candidate cand) {
    String constituent = cand.constituent;
    if (unaryRules.containsKey(constituent)) {
      List<GrammarRule> rules = unaryRules.get(constituent);
      for (GrammarRule rule : rules) {
        String lhs = rule.getLhs();
        double newWeight = cand.totalWeight + rule.getWeight();
        Candidate newCand = new Candidate(lhs, newWeight, cand);
        addCandidateToCell(cell, newCand);
      }
    }
  }

  // Ensures that a cell is under the size specified by beam search
  private void beamLimit (HashMap<String,Candidate> cell) {

    // Populate priority queue
    PriorityQueue<Candidate> pq = new PriorityQueue<>();
    for (String key : cell.keySet()) {
      pq.add(cell.get(key));
    }

    // Clear map and insert best weight elements only
    int top = Math.min(beamSize, cell.size());
    cell.clear();
    for (int i = 0; i < top; i += 1) {
      Candidate c = pq.poll();
      cell.put(c.constituent, c);
    }
  }

  // Populates the diagonal of the DP table with all lexical rules
  private void fillDpTableDiagonal (List<List<HashMap<String,Candidate>>> cells, String[] words) {
    // Fill every cell
    for (int i = 0; i < words.length; i += 1) {
      String word = words[i];
      HashMap<String, Candidate> cell = cells.get(i).get(i);

      // Check all lexical rules
      for (GrammarRule r : lexicalRules) {
        String rWord = r.getRhs().get(0);
        double weight = r.getWeight();
        if (word.equals(rWord)) {
          String constituent = r.getLhs();
          Candidate base = new Candidate(word, 0.0);
          Candidate cand = new Candidate(constituent, weight, base);
          addCandidateToCell(cell, cand);
        }
      }

      // In case of beam search
      if (beamSize > 0) beamLimit(cell);
    }
  }

  // Assumes the diagonal has already been filled in
  // Fills in rest of DP table with candidate parses
  private void fillDpTable (List<List<HashMap<String,Candidate>>> cells, String[] words) {
    for (int diag = 1; diag < words.length; diag += 1) {
      for (int row = 0; row < words.length - diag; row += 1) {
        int col = row + diag;

        HashMap<String,Candidate> curCell = cells.get(row).get(col);

        // Check all pairs of children
        for (int childI = 0; childI < diag; childI += 1) {
          int childARow = row;
          int childACol = col - diag + childI;
          int childBRow = row + 1 + childI;
          int childBCol = col;

          // Check all binary rules
          HashMap<String,Candidate> leftChild = cells.get(childARow).get(childACol);
          HashMap<String,Candidate> rightChild = cells.get(childBRow).get(childBCol);
          for (GrammarRule rule : binaryRules) {
            String lhs = rule.getLhs();
            ArrayList<String> rhs = rule.getRhs();
            String ls = rhs.get(0);
            String rs = rhs.get(1);
            if (leftChild.containsKey(ls) && rightChild.containsKey(rs)) {
              Candidate lCand = leftChild.get(ls);
              Candidate rCand = rightChild.get(rs);
              double newWeight = lCand.totalWeight + rCand.totalWeight + rule.getWeight();
              Candidate newCand = new Candidate(lhs, newWeight, lCand, rCand);
              addCandidateToCell(curCell, newCand);
            }
          } // Check all binary rules
        } // Check all pairs of children

        // If doing beam search
        if (beamSize > 0) beamLimit(curCell);
      }
    } // Fill every cell
  }

  private String parseSentence (String sentence) {

    // Extract individual words
    String[] words = sentence.split(" ");

    // Set up grid of cells: each cell is a hashtable: constituent -> candidate
    List<List<HashMap<String,Candidate>>> cells = new ArrayList<>();
    for (int r = 0; r < words.length; r += 1) {
      List<HashMap<String,Candidate>> row = new ArrayList<>();
      for (int c = 0; c < words.length; c += 1) {
        HashMap<String,Candidate> cell = new HashMap<>();
        row.add(cell);
      }
      cells.add(row);
    }

    fillDpTableDiagonal(cells, words);
    fillDpTable(cells, words);

    // Print final sentence
    HashMap<String,Candidate> rootCell = cells.get(0).get(words.length - 1);
    if (!rootCell.containsKey("S")) return "NULL";
    else return rootCell.get("S").toString(originalTree);
  }

  private void parseSentences (String sentencesFile) {
    try (BufferedReader br = new BufferedReader(new FileReader(sentencesFile))) {
      String line;
      while ((line = br.readLine()) != null) {
        System.out.println(parseSentence(line));
      }
    } catch (IOException e) {
      System.out.println("Error reading file: " + e);
    }
  }

  // Reads in rules from file
  private void buildRules (String grammarFile) {
    try (BufferedReader br = new BufferedReader(new FileReader(grammarFile))) {
      String line;
      while ((line = br.readLine()) != null) {
        GrammarRule rule = new GrammarRule(line);
        if (rule.isLexical()) lexicalRules.add(rule);
        else if (rule.numRhsElements() == 1) {
          String rhs = rule.getRhs().get(0);
          if (!unaryRules.containsKey(rhs)) {
            unaryRules.put(rhs, new ArrayList<>());
          }
          unaryRules.get(rhs).add(rule);
        }
        else binaryRules.add(rule);
      }
    } catch (IOException e) {
      System.out.println("Error reading file: " + e);
    }
  }

  public static void main (String[] args) {

    // Ensure appropriate args
    String grammarFile = "";
    String sentencesFile = "";
    boolean originalTree = false;
    int beamSize = 0;
    for (int i = 0; i < args.length; i += 1) {
      String arg = args[i];
      if (arg.equals("-original")) {
        originalTree = true;
      } else if (arg.equals("-beam")) {
        i += 1;
        beamSize = Integer.valueOf(args[i]);
      } else if (grammarFile.equals("")) {
        grammarFile = arg;
      } else {
        sentencesFile = arg;
      }
    }

    new CKY(grammarFile, sentencesFile, originalTree, beamSize);

  }

}
