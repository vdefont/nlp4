package nlp.parser;

import java.util.*;
import java.io.*;

/**
TODO:
- Clean up massive "parseSentence" method
- Test some more
- Add extra credit 
*/

class Candidate {
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

  public String toStringRec () {
    if (leftChild == null) return constituent;
    else if (rightChild == null) return "(" + constituent + " " + leftChild.toStringRec() + ")";
    else return "(" + constituent + " " + leftChild.toStringRec() + " " + rightChild.toStringRec() + ")";
  }

  public String toString () {
    return toStringRec() + " " + totalWeight;
  }
}

public class CKY {

  private static boolean DEBUG = false;

  private List<GrammarRule> lexicalRules, binaryRules;
  private Map<String,List<GrammarRule>> unaryRules; // rhs -> lhs

  public CKY (String grammarFile, String sentencesFile) {

    lexicalRules = new ArrayList<>();
    unaryRules = new HashMap<>();
    binaryRules = new ArrayList<>();

    // Read in rules from file
    buildRules(grammarFile);

    parseSentences(sentencesFile);

  }

  // Returns true if candidate added
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

  private String parseSentence (String sentence) {

    // Extract individual words
    String[] words = sentence.split(" ");
    int numWords = words.length;

    // Set up grid of cells: each cell is a hashtable: constituent -> candidate
    List<List<HashMap<String,Candidate>>> cells = new ArrayList<>();
    for (int r = 0; r < numWords; r += 1) {
      List<HashMap<String,Candidate>> row = new ArrayList<>();
      for (int c = 0; c < numWords; c += 1) {
        HashMap<String,Candidate> cell = new HashMap<>();
        row.add(cell);
      }
      cells.add(row);
    }

    // Populate lexical rules
    for (int i = 0; i < numWords; i += 1) {
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
    }

    // Populate everything else
    if (DEBUG) System.out.println();
    if (DEBUG) System.out.println(numWords);
    for (int diag = 1; diag < numWords; diag += 1) {
      for (int row = 0; row < numWords - diag; row += 1) {
        int col = row + diag;

        HashMap<String,Candidate> curCell = cells.get(row).get(col);

        if (DEBUG) System.out.println("Filling cell " + " (" + row + ", " + col + ")");
        // Check all pairs of children
        for (int childI = 0; childI < diag; childI += 1) {
          int childARow = row;
          int childACol = col - diag + childI;
          int childBRow = row + 1 + childI;
          int childBCol = col;
          if (DEBUG) System.out.printf(" children: (%d, %d) - (%d, %d)\n", childARow, childACol, childBRow, childBCol);

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
              if (addCandidateToCell(curCell, newCand)) {
                if (DEBUG) System.out.printf("  %s -> %s %s %s\n", lhs, ls, rs, "" + newWeight);
              }
            }
          }

        }
      }
    }

    // Print final sentence
    HashMap<String,Candidate> rootCell = cells.get(0).get(numWords - 1);
    if (!rootCell.containsKey("S")) return "NULL";
    else return rootCell.get("S").toString();
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
    String grammarFile = "../data/example.pcfg";
    String sentencesFile = "../data/example.input";
    new CKY(grammarFile, sentencesFile);
  }

}
