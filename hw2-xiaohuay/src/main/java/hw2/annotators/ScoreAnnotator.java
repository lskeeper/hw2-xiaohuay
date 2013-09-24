package hw2.annotators;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;

import edu.cmu.deiis.types.Annotation;
import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.AnswerScore;
import edu.cmu.deiis.types.NGram;
import edu.cmu.deiis.types.Question;
import edu.cmu.deiis.types.Token;

/**
 * 
 * @author xiaohua
 * 
 *         This annotator generates a score for each answer annotation, based on both token overlap
 *         and n-gram overlap scores measure by cosine similarity. The weights for token overlap and
 *         n-gram overlap are 0.8 and 0.2 respectively.
 * 
 * 
 */
public class ScoreAnnotator extends JCasAnnotator_ImplBase {
  double finalScore = 0.0;

  double documentCount = 0;

  public void initialize(UimaContext aContext) {

  }

  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    Question question = JCasUtil.selectSingle(aJCas, Question.class);
    Map<String, Integer> questionTokenCountMap = getCoveredTypeCounts(question, Token.class);
    Table<Integer, String, Integer> questionNGramCountMap = getCoveredNGramCounts(question);
    for (Answer answer : UimaConvenience.getAnnotationList(aJCas, Answer.class)) {
      Map<String, Integer> answerTokenCountMap = getCoveredTypeCounts(answer, Token.class);
      Table<Integer, String, Integer> answerNGramCountMap = getCoveredNGramCounts(answer);

      double tokenOverlapScore = getCosine(questionTokenCountMap, answerTokenCountMap);
      double nGramOverlapScore = getNGramScore(questionNGramCountMap, answerNGramCountMap);
      finalScore = 0.8 * tokenOverlapScore + 0.2 * nGramOverlapScore;

      AnswerScore answerScore = new AnswerScore(aJCas);
      answerScore.setAnswer(answer);
      answerScore.setScore(finalScore);
      answer.setConfidence(finalScore);
      answerScore.setCasProcessorId(this.getClass().getName());
      answerScore.addToIndexes();
    }
  }

  /**
   * Get the cosine similarity between two n-gram strings, which is the arithmetic average of all
   * n-gram pairs
   * 
   * @param questionNGramCountMap
   * @param answerNGramCountMap
   * @return the cosine similarity between two bags of n-grams in each annotation
   */
  private double getNGramScore(Table<Integer, String, Integer> questionNGramCountMap,
          Table<Integer, String, Integer> answerNGramCountMap) {
    int allNGramsCount = 0;
    double nGramOverlapScore = 0;
    // get the (n-gram, frequency) map
    Map<Integer, Map<String, Integer>> questionNGramRows = questionNGramCountMap.rowMap();
    for (Entry<Integer, Map<String, Integer>> answerNGramEntry : answerNGramCountMap.rowMap()
            .entrySet()) {

      Integer key = answerNGramEntry.getKey();
      double nGramScore = getCosine(questionNGramRows.get(key), answerNGramEntry.getValue());
      allNGramsCount++;
      nGramOverlapScore += nGramScore;
    }
    // avoid devide-by-0 error
    if (allNGramsCount > 0) {
      return nGramOverlapScore / allNGramsCount;
    } else {
      return 0;
    }
  }

  /**
   * Get the cosine similarity between two bag of words
   * 
   * @param bag1
   * @param bag2
   * @return The cosine similarity between two bag of words
   * 
   */
  private double getCosine(Map<String, Integer> bag1, Map<String, Integer> bag2) {
    if (bag1.isEmpty() || bag2.isEmpty()) {
      return 0;
    }

    double score = 0.0;
    for (Entry<String, Integer> tokenEntry : bag1.entrySet()) {
      String tokenString = tokenEntry.getKey();
      Integer count = tokenEntry.getValue();
      if (bag2.containsKey(tokenString)) {
        score += bag2.get(tokenString) * count;
      }
    }
    return score / Math.sqrt(getLength(bag1) * getLength(bag2));
  }

  /**
   * Get the Euclidean length of a bag of word
   * 
   * @param bag
   * @return The Euclidean length computed from the (word, frequency) map
   * 
   */
  private double getLength(Map<String, Integer> bag) {
    double result = 0;
    for (Entry<String, Integer> tokenEntry : bag.entrySet()) {
      Integer count = tokenEntry.getValue();
      result += count * count;
    }
    return result;
  }

  /**
   * Get the (ngram, frequency) map of the ngrams covered in the given annotation
   * 
   * @param annotation
   * @return The (ngram, frequency) map of the ngrams covered in the given annotation
   */

  private <T extends Annotation> Table<Integer, String, Integer> getCoveredNGramCounts(T annotation) {
    Table<Integer, String, Integer> nGramCountMap = HashBasedTable.create();
    for (NGram nGram : JCasUtil.selectCovered(NGram.class, annotation)) {
      Integer N = nGram.getElements().size();
      if (N == 1) {
        continue;
      }
      String nGramText = nGram.getCoveredText();
      if (nGramCountMap.contains(N, nGramText)) {

        nGramCountMap.put(N, nGramText, nGramCountMap.get(N, nGramText) + 1);
      } else {

        nGramCountMap.put(N, nGramText, 1);
      }
    }
    return nGramCountMap;
  }

  /**
   * Get the (word, frequency) map for the give annotation
   * 
   * @param annotation
   * @param clazz
   * @return The (word, frequency) map for the give annotation
   */
  private <A extends Annotation, T extends Annotation> Map<String, Integer> getCoveredTypeCounts(
          A annotation, Class<T> clazz) {
    Map<String, Integer> typeCountMap = new HashMap<String, Integer>();
    for (T token : JCasUtil.selectCovered(clazz, annotation)) {
      String tokenString = token.getCoveredText();
      if (Pattern.matches("\\p{Punct}", tokenString)) {
        continue;
      }
      if (typeCountMap.containsKey(tokenString)) {
        typeCountMap.put(tokenString, typeCountMap.get(tokenString) + 1);
      } else {
        typeCountMap.put(tokenString, 1);
      }
    }
    return typeCountMap;
  }
}
