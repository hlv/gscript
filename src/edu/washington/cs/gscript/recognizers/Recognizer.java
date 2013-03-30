package edu.washington.cs.gscript.recognizers;

import edu.washington.cs.gscript.framework.ReadWriteProperty;
import edu.washington.cs.gscript.helpers.GSMath;
import edu.washington.cs.gscript.models.*;
import libsvm.*;

import java.util.ArrayList;
import java.util.Arrays;

public class Recognizer {

    public static double crossValidation(svm_problem prob, svm_parameter param, int nr_fold) {
        int i;
        int total_correct = 0;
        double total_error = 0;
        double sumv = 0, sumy = 0, sumvv = 0, sumyy = 0, sumvy = 0;
        double[] target = new double[prob.l];

        double accu = 0;

        svm.svm_cross_validation(prob, param, nr_fold, target);
        if(param.svm_type == svm_parameter.EPSILON_SVR ||
                param.svm_type == svm_parameter.NU_SVR)
        {
            for(i=0;i<prob.l;i++)
            {
                double y = prob.y[i];
                double v = target[i];
                total_error += (v-y)*(v-y);
                sumv += v;
                sumy += y;
                sumvv += v*v;
                sumyy += y*y;
                sumvy += v*y;
            }
            System.out.print("Cross Validation Mean squared error = "+total_error/prob.l+"\n");
            System.out.print("Cross Validation Squared correlation coefficient = "+
                    ((prob.l*sumvy-sumv*sumy)*(prob.l*sumvy-sumv*sumy))/
                            ((prob.l*sumvv-sumv*sumv)*(prob.l*sumyy-sumy*sumy))+"\n"
            );
        }
        else
        {
            for(i=0;i<prob.l;i++)
                if(target[i] == prob.y[i])
                    ++total_correct;
            accu = 100.0*total_correct/prob.l;
            System.out.print("Cross Validation Accuracy = "+accu+"%\n");
        }

        return accu;
    }

    private static svm_node[] featuresToSVMNode(double[] features) {
        svm_node[] x = new svm_node[features.length];
        for (int i = 0; i < features.length; ++i) {
            x[i] = new svm_node();
            x[i].index = i + 1;
            x[i].value = 1 - features[i];
        }

        return x;
    }

    public double train(Project project, ReadWriteProperty<Integer> progress, int progressTotal) {

        this.project = project;

        int currentProgress = progress.getValue();

        ArrayList<svm_node[]> xList = new ArrayList<svm_node[]>();
        ArrayList<Double> yList = new ArrayList<Double>();

        int maxIndex = 0;
        int numOfCategories = project.getNumOfCategories();
        for (int catIndex = 0; catIndex < numOfCategories; ++catIndex) {
            Category category = project.getCategory(catIndex);
            int numOfSamples = category.getNumOfSamples();

            if (numOfSamples == 0) {
                continue;
            }

            for (int sampleIndex = 0; sampleIndex < numOfSamples; ++sampleIndex) {
                Gesture sample = category.getSample(sampleIndex);
                double[] features = generateSVMFeatures(sample, project, useRotationFeatures, useScaleFeatures);
                svm_node[] x = featuresToSVMNode(features);
                maxIndex = x.length;
                xList.add(x);
                yList.add((double)catIndex);

                System.out.print(catIndex);
                for (int i = 0; i < features.length; ++i) {
                    System.out.print(" " + x[i].index + ":" + x[i].value);
                }
                System.out.println();
            }

            progress.setValue(currentProgress + (int)((catIndex + 1) / (double) numOfCategories * 0.9 * progressTotal));
        }

        double accuracy = 0;

        if (yList.size() > 0) {

            initScale(xList);
            lower = -1;
            upper = 1;

            for (svm_node[] x : xList) {
                scale(x);
            }

            svm_problem problem = new svm_problem();
            problem.l = yList.size();
            problem.x = new svm_node[problem.l][];
            problem.y = new double[problem.l];

            for (int i = 0; i < problem.l; ++i) {
                problem.x[i] = xList.get(i);
                problem.y[i] = yList.get(i);
            }

            svm_parameter param = new svm_parameter();
            // default values
            param.svm_type = svm_parameter.LINEAR;
            param.kernel_type = svm_parameter.RBF;
            param.degree = 3;
            param.gamma = 0;	// 1/num_features
            param.coef0 = 0;
            param.nu = 0.5;
            param.cache_size = 100;
            param.C = 1;
            param.eps = 1e-3;
            param.p = 0.1;
            param.shrinking = 1;
            param.probability = 0;
            param.nr_weight = 0;
            param.weight_label = new int[0];
            param.weight = new double[0];

            param.gamma = 1.0 / maxIndex;

            model = svm.svm_train(problem, param);

            accuracy = crossValidation(problem, param, 10);
        }

        progress.setValue(currentProgress + progressTotal);
        return accuracy;
    }

    public static double[] generateSVMFeatures(Gesture gesture, Project project, boolean useAngle, boolean useScale) {
        int numOfCategories = project.getNumOfCategories();

        ArrayList<Double> featureList = new ArrayList<Double>();

        for (int catIndex = 0; catIndex < numOfCategories; ++catIndex) {
            Category category = project.getCategory(catIndex);

            if (category.getNumOfSamples() == 0) {
                continue;
            }

            featureList.add(minDistance(gesture, category) / Learner.MAX_LOSS);

            int numOfShapes = category.getNumOfShapes();
            if (numOfShapes <= 1 && !category.getShape(0).isRepeatable()) {
                continue;
            }

            ArrayList<ArrayList<PartMatchResult>> matches = new ArrayList<ArrayList<PartMatchResult>>();
            Learner.findPartsInGesture(gesture, category.getShapes(), matches);
            if (matches.size() == 0) {

                for (int shapeIndex = 0; shapeIndex < numOfShapes; ++shapeIndex) {
                    featureList.add(1.0);
                }

            } else {
                for (int shapeIndex = 0; shapeIndex < numOfShapes; ++shapeIndex) {
                    ShapeSpec shape = category.getShape(shapeIndex);
                    PartMatchResult match = matches.get(shapeIndex).get(0);
                    featureList.add(match.getScore() / Learner.MAX_LOSS);

                    if (useAngle) {
                        if (shapeIndex == 0) {
                            featureList.add(match.getAlignedAngle());
                        } else {
                            ArrayList<PartMatchResult> lastSubMatches = matches.get(shapeIndex - 1);
                            PartMatchResult lastMatch = lastSubMatches.get(lastSubMatches.size() - 1);
                            featureList.add(match.getAlignedAngle() - lastMatch.getAlignedAngle());
                        }
                    }
                }

                if (useScale) {
                    for (int i = 0; i < numOfShapes - 1; ++i) {
                        for (int j = i + 1; j < numOfShapes; ++j) {
                            double si = GSMath.boundingCircle(matches.get(i).get(0).getMatchedFeatureVector().getFeatures())[2];
                            double sj = GSMath.boundingCircle(matches.get(j).get(0).getMatchedFeatureVector().getFeatures())[2];
                            featureList.add(sj / si);
                        }
                    }
                }
            }
        }

        double[] features = new double[featureList.size()];
        for (int i = 0; i < features.length; ++i) {
            features[i] = featureList.get(i);
        }

        return features;
    }

    private static double minDistance(Gesture gesture, Category category) {
        double minDistance = Double.POSITIVE_INFINITY;

        double[] fu = Learner.gestureFeatures(gesture, Learner.NUM_OF_RESAMPLING);
        int numOfSamples = category.getNumOfSamples();
        for (int sampleIndex = 0; sampleIndex < numOfSamples; ++sampleIndex) {
            Gesture sample = category.getSample(sampleIndex);
//            if (sample == gesture) {
//                continue;
//            }

            double[] fv = Learner.gestureFeatures(sample, Learner.NUM_OF_RESAMPLING);
            double d = Learner.distanceToTemplateAligned(fu, fv);

            if (GSMath.compareDouble(d, minDistance) < 0) {
                minDistance = d;
            }
        }

        return minDistance;
    }


    private Project project;
    private svm_model model;

    private double[] fMax;
    private double[] fMin;

    private double lower = -1;
    private double upper = 1;

    private boolean useRotationFeatures;
    private boolean useScaleFeatures;

    public Recognizer() {
        useRotationFeatures = true;
        useScaleFeatures = true;
    }

    public Project getProject() {
        return project;
    }

    private void scale(svm_node[] x) {
        for (int i = 0; i < fMax.length; ++i) {
            if (x[i].value == fMax[i]) {
                x[i].value = upper;
            } else if (x[i].value == fMin[i]) {
                x[i].value = lower;
            } else {
                x[i].value = GSMath.linearInterpolate(lower, upper, (x[i].value - fMin[i]) / (fMax[i] - fMin[i]));
            }
        }
    }

    private void initScale(ArrayList<svm_node[]> xList) {

        int fLength = xList.get(0).length;
        fMax = new double[fLength];
        fMin = new double[fLength];

        Arrays.fill(fMax, Double.NEGATIVE_INFINITY);
        Arrays.fill(fMin, Double.POSITIVE_INFINITY);

        for (svm_node[] x : xList) {
            for (int i = 0; i < fLength; ++i) {
                fMax[i] = Math.max(fMax[i], x[i].value);
                fMin[i] = Math.min(fMin[i], x[i].value);
            }
        }

    }

    public Category classify(Gesture gesture) {
        double[] features = generateSVMFeatures(gesture, project, useRotationFeatures, useScaleFeatures);
        svm_node[] x = featuresToSVMNode(features);
        scale(x);
        int categoryIndex = (int) svm.svm_predict(model, x);
        return project.getCategory(categoryIndex);
    }
}
