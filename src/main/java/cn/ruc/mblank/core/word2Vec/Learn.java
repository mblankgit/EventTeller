package cn.ruc.mblank.core.word2Vec;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import cn.ruc.mblank.util.Const;
import cn.ruc.mblank.util.Haffman;
import cn.ruc.mblank.util.Similarity;
import cn.ruc.mblank.util.TimeUtil;
import love.cq.util.MapCount;
import cn.ruc.mblank.core.word2Vec.domain.HiddenNeuron;
import cn.ruc.mblank.core.word2Vec.domain.Neuron;
import cn.ruc.mblank.core.word2Vec.domain.WordNeuron;

public class Learn {

    private Map<String, Neuron> wordMap = new HashMap<String, Neuron>();
    /**
     * 训练多少个特征
     */
    private int layerSize = 100;

    /**
     * 上下文窗口大小
     */
    private int window = 5;

    private double sample = 1e-3;
    private double alpha = 0.025;
    private double startingAlpha = alpha;

    public int EXP_TABLE_SIZE = 1000;

    private Boolean isCbow = true;

    private double[] expTable = new double[EXP_TABLE_SIZE];

    private int trainWordsCount = 0;

    private int MAX_EXP = 6;

    public Learn(Boolean isCbow, Integer layerSize, Integer window, Double alpha, Double sample) {
        createExpTable();
        if (isCbow != null) {
            this.isCbow = isCbow;
        }
        if (layerSize != null)
            this.layerSize = layerSize;
        if (window != null)
            this.window = window;
        if (alpha != null)
            this.alpha = alpha;
        if (sample != null)
            this.sample = sample;
    }

    public Learn() {
        createExpTable();
    }

    /**
     * trainModel
     * @throws java.io.IOException
     */
    private void trainModel(File file) throws IOException {
        try{
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String temp = null;
            long nextRandom = 5;
            int wordCount = 0;
            int lastWordCount = 0;
            int wordCountActual = 0;
            while ((temp = br.readLine()) != null) {
                String[] its = temp.split("\t");
                if(its.length != 2){
                    continue;
                }
                int day = 0;
                try{
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-DD hh:mm:ss");
                    day = TimeUtil.getDayGMT8(sdf.parse(its[0]));

                }catch (Exception e){
                    //parse date str failed...
                    continue;
                }
                if (wordCount - lastWordCount > 1000000) {
                    System.out.println("alpha:" + alpha + "\tProgress: "
                            + (int) (wordCountActual / (double) (trainWordsCount + 1) * 100)
                            + "%");
                    wordCountActual += wordCount - lastWordCount;
                    lastWordCount = wordCount;
                    alpha = startingAlpha * (1 - wordCountActual / (double) (trainWordsCount + 1));
                    if (alpha < startingAlpha * 0.0001) {
                        alpha = startingAlpha * 0.0001;
                    }
                }
                String[] strs = its[1].split(" ");
                wordCount += strs.length;
                List<WordNeuron> sentence = new ArrayList<WordNeuron>();
                for (int i = 0; i < strs.length; i++) {
                    Neuron entry = wordMap.get(strs[i]);
                    if (entry == null) {
                        continue;
                    }
                    // The subsampling randomly discards frequent words while keeping the ranking same
                    if (sample > 0) {
                        double ran = (Math.sqrt(entry.freq / (sample * trainWordsCount)) + 1)
                                * (sample * trainWordsCount) / entry.freq;
                        nextRandom = nextRandom * 25214903917L + 11;
                        if (ran < (nextRandom & 0xFFFF) / (double) 65536) {
                            continue;
                        }
                    }
                    sentence.add((WordNeuron) entry);
                }


                for (int index = 0; index < sentence.size(); index++) {
                    nextRandom = nextRandom * 25214903917L + 11;
                    if (isCbow) {
                        cbowGram(index, sentence, (int) nextRandom % window,day);

                    } else {
                        skipGram(index, sentence, (int) nextRandom % window);
                    }
                }

            }
            System.out.println("Vocab size: " + wordMap.size());
            System.out.println("Words in train file: " + trainWordsCount);
            System.out.println("sucess train over!");
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     *
     * @param index
     * @param sentence
     * @param b
     */
    private void skipGram(int index, List<WordNeuron> sentence, int b) {
        // TODO Auto-generated method stub
        WordNeuron word = sentence.get(index);
        int a, c = 0;
        for (a = b; a < window * 2 + 1 - b; a++) {
            if (a == window) {
                continue;
            }
            c = index - window + a;
            if (c < 0 || c >= sentence.size()) {
                continue;
            }

            double[] neu1e = new double[layerSize];//误差项
            //HIERARCHICAL SOFTMAX
            List<Neuron> neurons = word.neurons;
            WordNeuron we = sentence.get(c);
            for (int i = 0; i < neurons.size(); i++) {
                HiddenNeuron out = (HiddenNeuron) neurons.get(i);
                double f = 0;
                // Propagate hidden -> output
                for (int j = 0; j < layerSize; j++) {
                    f += we.syn0[j] * out.syn1[j];
                }
                if (f <= -MAX_EXP || f >= MAX_EXP) {
                    continue;
                } else {
                    f = (f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2);
                    f = expTable[(int) f];
                }
                // 'g' is the gradient multiplied by the learning rate
                double g = (1 - word.codeArr[i] - f) * alpha;
                // Propagate errors output -> hidden
                for (c = 0; c < layerSize; c++) {
                    neu1e[c] += g * out.syn1[c];
                }
                // Learn weights hidden -> output
                for (c = 0; c < layerSize; c++) {
                    out.syn1[c] += g * we.syn0[c];
                }
            }

            // Learn weights input -> hidden
            for (int j = 0; j < layerSize; j++) {
                we.syn0[j] += neu1e[j];
            }
        }

    }

    /**
     * 词袋模型
     * @param index
     * @param sentence
     * @param b
     */
    private void cbowGram(int index, List<WordNeuron> sentence, int b,int day) {
        WordNeuron word = sentence.get(index);
        int a, c = 0;

        List<Neuron> neurons = word.neurons;
        double[] neu1e = new double[layerSize];//误差项
        double[] neu1 = new double[layerSize];//误差项
        WordNeuron last_word;

        for (a = b; a < window * 2 + 1 - b; a++)
            if (a != window) {
                c = index - window + a;
                if (c < 0)
                    continue;
                if (c >= sentence.size())
                    continue;
                last_word = sentence.get(c);
                if (last_word == null)
                    continue;
                for (c = 0; c < layerSize; c++)
                    neu1[c] += last_word.syn0[c];
            }

        //HIERARCHICAL SOFTMAX
        for (int d = 0; d < neurons.size(); d++) {
            HiddenNeuron out = (HiddenNeuron) neurons.get(d);
            double f = 0;
            // Propagate hidden -> output
            for (c = 0; c < layerSize; c++)
                f += neu1[c] * out.syn1[c];
            if (f <= -MAX_EXP)
                continue;
            else if (f >= MAX_EXP)
                continue;
            else
                f = expTable[(int) ((f + MAX_EXP) * (EXP_TABLE_SIZE / MAX_EXP / 2))];
            double g = f * (1 - f) * (word.codeArr[d] - f) * alpha;
            for (c = 0; c < layerSize; c++) {
                neu1e[c] += g * out.syn1[c];
            }
            // Learn weights hidden -> output
            for (c = 0; c < layerSize; c++) {
                out.syn1[c] += g * neu1[c];
            }
        }
        //hidden to input
        for (a = b; a < window * 2 + 1 - b; a++) {
            if (a != window) {
                c = index - window + a;
                if (c < 0)
                    continue;
                if (c >= sentence.size())
                    continue;
                last_word = sentence.get(c);
                if (last_word == null)
                    continue;
                //if bigger than max, record the old one
                for (c = 0; c < layerSize; c++)
                    last_word.syn0[c] += neu1e[c];
                //check the distance change of new vector
                double[] tvs = new double[layerSize];
                //get old vector
                for(c = 0 ; c < layerSize; ++c){
                    tvs[c] = last_word.syn0[c] - neu1e[c];
                }
//                double sim = Similarity.simOf2Vector(tvs,last_word.syn0);
//                if(sim < Const.MaxVectorSimChange){
//                    System.out.println(last_word.name + "\t" + sim);
                    last_word.synMap.put(day, tvs);
//                }
            }
        }
    }

    /**
     * 统计词频
     * @param file
     * @throws java.io.IOException
     */
    private void readVocab(File file) throws IOException {
        MapCount<String> mc = new MapCount<String>();
        try{
            int line = 0;
            BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            String temp = null;
            while ((temp = br.readLine()) != null) {
                ++line;
                String[] its = temp.split("\t");
                if(its.length != 2){
                    continue;
                }
                String[] split = its[1].split(" ");
                trainWordsCount += split.length;
                for (String string : split) {
                    mc.add(string);
                }
                if(line % 10000 == 0){
                    System.out.println(line);
                }
            }
        }catch (Exception e){

        }
        for (Entry<String, Integer> element : mc.get().entrySet()) {
            wordMap.put(element.getKey(), new WordNeuron(element.getKey(), element.getValue(),layerSize));
        }
    }

    /**
     * Precompute the exp() table
     * f(x) = x / (x + 1)
     */
    private void createExpTable() {
        for (int i = 0; i < EXP_TABLE_SIZE; i++) {
            expTable[i] = Math.exp(((i / (double) EXP_TABLE_SIZE * 2 - 1) * MAX_EXP));
            expTable[i] = expTable[i] / (expTable[i] + 1);
        }
    }

    /**
     * 根据文件学习
     * @param file
     * @throws java.io.IOException
     */
    public void learnFile(File file) throws IOException {
        //read file words to memory map
        readVocab(file);
        new Haffman(layerSize).make(wordMap.values());
        //查找每个神经元
        for (Neuron neuron : wordMap.values()) {
            ((WordNeuron)neuron).makeNeurons() ;
        }
        System.out.println("start to train the model" + "\t" + wordMap.size() + "\t" + trainWordsCount);
        trainModel(file);
    }

    /**
     * format
     * wordSize layerSize
     * wordName,totalVector,TimeVectorSize,Time1,Vector1,Time2,Vector2...
     * @param file
     */
    public void saveModel(File file) {
        int totalVectorSize = 0;
        try{
            DataOutputStream dataOutputStream = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
            dataOutputStream.writeInt(wordMap.size());
            dataOutputStream.writeInt(layerSize);
            double[] syn0 = null;
            for (Entry<String, Neuron> element : wordMap.entrySet()) {
                dataOutputStream.writeUTF(element.getKey());
                //for total vector
                syn0 = ((WordNeuron) element.getValue()).syn0;
                for (double d : syn0) {
                    dataOutputStream.writeFloat(((Double) d).floatValue());
                }
                //for vector in old time
                dataOutputStream.writeInt(((WordNeuron) element.getValue()).synMap.size());
                for(int day : ((WordNeuron) element.getValue()).synMap.keySet()){
                    dataOutputStream.writeInt(day);
                    totalVectorSize++;
                    syn0 = ((WordNeuron) element.getValue()).synMap.get(day);
                    for(double d : syn0){
                        dataOutputStream.writeFloat(((Double) d).floatValue());
                    }
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(wordMap.size() + "\t" + totalVectorSize);
    }

    public int getLayerSize() {
        return layerSize;
    }

    public void setLayerSize(int layerSize) {
        this.layerSize = layerSize;
    }

    public int getWindow() {
        return window;
    }

    public void setWindow(int window) {
        this.window = window;
    }

    public double getSample() {
        return sample;
    }

    public void setSample(double sample) {
        this.sample = sample;
    }

    public double getAlpha() {
        return alpha;
    }

    public void setAlpha(double alpha) {
        this.alpha = alpha;
        this.startingAlpha = alpha;
    }

    public Boolean getIsCbow() {
        return isCbow;
    }

    public void setIsCbow(Boolean isCbow) {
        this.isCbow = isCbow;
    }

    public static void main(String[] args) throws IOException {
        String trainFilePath = args[0];
//        String trainFilePath = "d:\\ETT\\tianyiwords";
        String vectorSavePath = trainFilePath + "_vector.bin";
        Learn learn = new Learn();
        long start = System.currentTimeMillis() ;
        learn.learnFile(new File(trainFilePath));
        System.out.println("use time "+(System.currentTimeMillis()-start));
        learn.saveModel(new File(vectorSavePath));
        
    }
}
