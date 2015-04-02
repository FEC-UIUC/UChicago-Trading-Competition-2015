

import java.io.*;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by Greg Pastorek on 3/11/2015.
 */
public class Main {

    static boolean[] tradables = new boolean[30];
    static double[] weights = new double[30];
    static double[] last_weights = new double[30];
    static double[] stock_values = new double[30];
    static double index_value;
    static double last_index_value;
    static double k = 1;

    static RegulationTuple nextAnnouncement = null;
    static Queue<RegulationTuple> regulationQueue = new LinkedList<RegulationTuple>();

    static File dataRootDir = new File("C:\\Users\\Greg Pastorek\\Documents\\FEC\\uchicago-algo\\indexCase\\market-data\\round1");
    static File capWeightsFile = new File(dataRootDir, "capWeights.csv");
    static File pricesFile = new File(dataRootDir, "prices.csv");
    static File tradableChangesFile = new File(dataRootDir, "tradable_changes.csv");
    static File tradableInitFile = new File(dataRootDir, "tradable_init.csv");

    private static void initTradables() throws IOException {
        BufferedReader tradableInitReader = new BufferedReader(new FileReader(tradableInitFile));
        int i = 0;
        String line;
        while ((line = tradableInitReader.readLine()) != null){
            tradables[i] = (Integer.parseInt(line) == 1);
            i++;
        }
    }

    private static void initWeights() throws IOException {
        BufferedReader capWeightsReader = new BufferedReader(new FileReader(capWeightsFile));
        int i = 0;
        String line;
        while ((line = capWeightsReader.readLine()) != null){
            weights[i] = Double.parseDouble(line);
            i++;
        }
    }

    private static void updatePrices(String[] line_split){
        for(int i = 0; i < 30; i++){
            stock_values[i] = Double.parseDouble(line_split[i]);
        }
        index_value = Double.parseDouble(line_split[30]);
    }

    private static double computeTickScore(){

        double portfolio_value = 0;
        double last_portfolio_value = 0;

        for(int i = 0; i < 30; i++){
            last_portfolio_value += k*last_weights[i]*stock_values[i];
        }

        for(int i = 0; i < 30; i++){
            portfolio_value += k*weights[i]*stock_values[i];
        }

        double log_p_val = Math.log(portfolio_value / last_portfolio_value);
        double log_i_val = Math.log(index_value / last_index_value);

        double difference = log_p_val - log_i_val;

        return difference*difference;
    }

    public static void main(String args[]) throws Exception {

        double[] myweights;
        double score = 0;

        TestIndexCase mycase = new TestIndexCase();

        mycase.initializeAlgo(/* add params here*/);

        BufferedReader pricesReader = new BufferedReader(new FileReader(pricesFile));
        BufferedReader tradableChangesReader = new BufferedReader(new FileReader(tradableChangesFile));

        pricesReader.readLine();

        initTradables();
        initWeights();

        /* get initial prices */
        updatePrices(pricesReader.readLine().split(","));

        /* set initial last_portfolio_value and last_index_value */
        last_index_value = index_value;
        last_weights = weights;

        /* call initial position */
        myweights = mycase.initalizePosition(stock_values, index_value, weights, tradables);

        int tick = 1;
        String line;
        while ((line = pricesReader.readLine()) != null) {

            /* check for announcements */
            if(nextAnnouncement == null){
                String tcr_line = tradableChangesReader.readLine();
                String[] tcr_line_split = tcr_line.split(",");
                int regTick = Integer.parseInt(tcr_line_split[0]) + 20;
                int regStock = Integer.parseInt(tcr_line_split[1]);
                boolean regTradable = (Integer.parseInt(tcr_line_split[1]) == 1);
                nextAnnouncement = new RegulationTuple(regTick, regStock, regTradable);
            }

            /* check if its time to release the next announcement */
            if(nextAnnouncement.tick == tick - 20) {
                regulationQueue.add(nextAnnouncement);
                boolean[] t = tradables.clone();
                t[nextAnnouncement.stock] = nextAnnouncement.tradable;
                mycase.regulationAnnouncement(tick, tick+20, t);
            }

            /* check for updates */
            if(!regulationQueue.isEmpty()){
                RegulationTuple r = regulationQueue.peek();
                if(r.tick == tick){
                    tradables[r.stock] = r.tradable;
                    regulationQueue.poll();
                }
            }

            /* update prices */
            String[] line_split = line.split(",");
            updatePrices(line_split);
            double[] new_weights = mycase.updatePosition(tick, stock_values, index_value);

            /* check that weights sum to 1 */
            double check_sum = 0;
            for(int i = 0; i < 30; i++){
                check_sum += new_weights[i];
            }

            if(Math.abs(check_sum - 1.0) > 1e-10){
                System.out.println("Weights don't sum to 1.0 +- 1e-10");
                throw new Exception();
            }


            /* compute transcation costs */
            for(int i = 0; i < 30; i++){
                score -= (Math.exp(Math.abs(weights[i] - new_weights[i])) - 1) / 200.0;
            }

            last_weights = weights;

            weights = new_weights;

            /* update score */
            score -= computeTickScore();

            tick++;

            System.out.println("Score = " + score);

        }

    }

    private static class RegulationTuple {
        int tick;
        int stock;
        boolean tradable;
        RegulationTuple(int t, int s, boolean tr){
            tick = t;
            stock = s;
            tradable = tr;
        }
    }

}
