package com.juvetic.rssi.util.formulas;

import static org.ejml.ops.CommonOps.addEquals;
import static org.ejml.ops.CommonOps.mult;
import static org.ejml.ops.CommonOps.multTransA;
import static org.ejml.ops.CommonOps.multTransB;
import static org.ejml.ops.CommonOps.subtract;
import static org.ejml.ops.CommonOps.subtractEquals;

import java.util.List;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.ops.CommonOps;

/**
 * Algorithm class which estimates user position by applying Extended Kalman Filter with static
 * mobile case.
 *
 * For a more detailed explanation on how this algorithm has been derived and deployed,
 * please refer to MSc Thesis.
 *
 * NOTE: A Java matrix library will be used to handle matrix operations in an efficient way.
 * EJML has been chosen because its good performance showed at Java Matrix Benchmark
 * (https://code.google.com/p/java-matrix-benchmark/)
 */
public class EKFAlgorithmData {

    // system state estimate
    public DenseMatrix64F x, P;

    private int dimenX = 1;

    // kinematics description
    private DenseMatrix64F F, H, Q, R;

    private int dimenZ = 1;

    private double[] computed_dist;

    // these are predeclared for efficiency reasons
    private DenseMatrix64F a, b;

    private DenseMatrix64F y, z, S, S_inv, c, d;

    private DenseMatrix64F K;

    private LinearSolver<DenseMatrix64F> solver;

    /**
     * Constructors
     */

    public EKFAlgorithmData(double[] x, double[][] P) {
        this.x = new DenseMatrix64F(x.length, 1, false, x);
        this.P = new DenseMatrix64F(P);
    }

    public EKFAlgorithmData(DenseMatrix64F x, DenseMatrix64F P) {
        this.x = x;
        this.P = P;
    }

    /**
     * Main method which initializes main paramenters x, P, F, H, Q and R,
     * and then apply prediction and correction stages.
     *
     * @param algorithmInputDataList List of APAlgorithmData objects with the 4 AP data  (BSSID -
     *                               estimated distance - RSS)
     * @param initialEstimates       Vector x and matrix P corresponding to the initial estimates of
     *                               state mean and covariance.
     * @return state mean and covariance estimated after EKF
     */

    public EKFAlgorithmData applyEKFAlgorithm(List<APAlgorithmData> algorithmInputDataList,
            EKFAlgorithmData initialEstimates) {

        /** Creation of parameters x, P, F, H, Q and R */
        creationAPrioriEstimates(initialEstimates);

        creationPredeclaredVariables();

        creationJacobianMatrices(algorithmInputDataList);

        creationNoiseCovarianceMatrices();

        /** Prediction step */
        predict();

        /** Update step */
        update();

        return new EKFAlgorithmData(x, P);
    }

    private void creationAPrioriEstimates(EKFAlgorithmData initialEstimates) {
        // Initial mean x
        x = initialEstimates.x;

        // Initial covariance matrix P
        P = initialEstimates.P;

    }

    private void creationPredeclaredVariables() {
        a = new DenseMatrix64F(dimenX, 1);
        b = new DenseMatrix64F(dimenX, dimenX);
        y = new DenseMatrix64F(dimenZ, 1);
        z = new DenseMatrix64F(dimenZ, 1);

        S = new DenseMatrix64F(dimenZ, dimenZ);
        S_inv = new DenseMatrix64F(dimenZ, dimenZ);
        c = new DenseMatrix64F(dimenZ, dimenX);
        d = new DenseMatrix64F(dimenX, dimenZ);
        K = new DenseMatrix64F(dimenX, dimenZ);

        // covariance matrices are symmetric positive semi-definite
        solver = LinearSolverFactory.symmPosDef(dimenX);

        // Computed distance between nominal point x and AP coordinates
        computed_dist = new double[dimenZ];
    }

    private void creationJacobianMatrices(List<APAlgorithmData> algorithmInputDataList) {
        /* F, Jacobian of f */
        F = CommonOps.identity(1);

        /* H, Jacobian of h */
        H = CommonOps.identity(1);
        // H = new DenseMatrix64F(dimenZ, dimenX);

        // int i;
        // double temp;
        //
        // // Filling computed distance vector
        // for (i = 0; i < dimenZ; i++){
        //     temp = Math.pow((x.get(0) - algorithmInputDataList.get(i).coordinatesAP.x),2) +
        //             Math.pow((x.get(1) - algorithmInputDataList.get(i).coordinatesAP.y),2);
        //     computed_dist[i] = Math.sqrt(temp);
        // }
        // // Filling matrix H
        // double hijx, hijy;
        // for (i = 0; i < dimenZ; i++){
        //     hijx = (x.get(0) - algorithmInputDataList.get(i).coordinatesAP.x)/computed_dist[i];
        //     hijy = (x.get(1) - algorithmInputDataList.get(i).coordinatesAP.y)/computed_dist[i];
        //     H.set(i, 0, hijx);
        //     H.set(i, 1, hijy);
    }

    private void creationNoiseCovarianceMatrices() {
        // Q, process noise covariance matrix
        double[] sigma_xy = new double[]{0.001};
        Q = CommonOps.diag(sigma_xy);

        // R, measurement noise covariance matrix
        // R0 = 0.1 seems fine, = 1, not that good, try lower
        double[] sigma_meas = new double[]{0.1};
        R = CommonOps.diag(sigma_meas);
    }

    private void predict() {
        // x = F x
        mult(F, x, a);
        x.set(a);

        // P = F P F' + Q
        mult(F, P, b);
        multTransB(b, F, P);
        addEquals(P, Q);
    }

    private void update() {
        // S = H P H' + R
        mult(H, P, c);
        multTransB(c, H, S);
        addEquals(S, R);

        // K = PH'S^(-1)
        if (!solver.setA(S)) {
            throw new RuntimeException("Invert failed");
        }
        solver.invert(S_inv);
        multTransA(H, S_inv, d);
        mult(P, d, K);

        /*
         *  z = y - h_x
         *      with y being a vector with measured distances using sensor between APs and MS
         *      h_x being a vector with computed distances using nominal point between APs and MS
         */
        // Transform double[] (computed_dist) to DenseMatrix64F (h_x)
        DenseMatrix64F h_x = new DenseMatrix64F(dimenZ, 1, false, computed_dist);
        subtract(y, h_x, z);

        // x = x + Kz
        mult(K, z, a);
        addEquals(x, a);

        // P = (I-kH)P = P - (KH)P = P-K(HP)
        mult(H, P, c);
        mult(K, c, b);
        subtractEquals(P, b);
    }

}
