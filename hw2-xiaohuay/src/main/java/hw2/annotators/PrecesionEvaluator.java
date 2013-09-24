package hw2.annotators;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import edu.cmu.deiis.types.Answer;
import edu.cmu.deiis.types.Question;

/**
 * This class outputs the precision at N for each question and their corresponding answers based on
 * the ground truth
 * 
 * @author xiaohua
 * 
 */
public class PrecesionEvaluator extends JCasAnnotator_ImplBase {
  double totalScore = 0;

  int documentCount = 0;

  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    List<Answer> answers = UimaConvenience.getAnnotationList(aJCas, Answer.class);
    Question question = JCasUtil.selectSingle(aJCas, Question.class);
    double totalCorrect = 0.0;

    for (Answer answer : answers) {
      if (answer.getIsCorrect()) {
        totalCorrect += 1;
      }
    }
    // sort the answers based on their confidence (score)
    Collections.sort(answers, new Comparator<Answer>() {
      public int compare(Answer ans1, Answer ans2) {
        return ans1.getConfidence() > ans2.getConfidence() ? -1 : 1;
      }
    });

    int numCorrect = 0;
    for (int i = 0; i < totalCorrect; i++) {
      if (answers.get(i).getIsCorrect()) {
        numCorrect++;
      }
    }

    double precisionAtN = numCorrect / totalCorrect;
    // output the precision in the console
    System.out.println(String.format("Question: %s", question.getCoveredText()));

    for (Answer answer : answers) {
      String correctInd = answer.getIsCorrect() ? "+" : "-";
      System.out.println(String.format("%s %.2f %s", correctInd, answer.getConfidence(),
              answer.getCoveredText()));
    }

    System.out.println(String.format("Precision at %d: %.2f ", (int) totalCorrect, precisionAtN));
    System.out.println();

    totalScore += precisionAtN;
    documentCount += 1;

  }

  public void collectionProcessComplete() {
    System.out.println("Average precision: " + totalScore / documentCount);
  }
}
