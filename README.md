EventTeller
===========

EventTeller is an Event Detection and Topic Tracking System.

It crawls news from more than 30 news sites in China real-time (the time interval is 20 minutes now).

The details about which website was crawled can be found in Wiki page.

You can find all the details in Qun Zhao's master dissertation.(http://pan.baidu.com/s/1eQotzm6)

Features
==========

1. News Crawler and News Infomation Extraction
2. News articles duplicate removal (using SimHash,in next version will use lucene or solr to improve efficiency)
3. Event Detection 
4. Build Event Evolution Structure.
5. Person and Location Relationships in Events
6. Word Vector(Based on Google Model Word2vec) to show relationships
7. Word Vector with time, same word in different time will have different vector.


Demo
========

You can try our online demo site in http://222.29.197.240:8080/EventTellerWeb

Thanks
========

Thanks to Ansj (A Chinese Tokenizer https://github.com/ansjsun/ansj_seg)

DB-IIR Lab Renmin University of China (http://iir.ruc.edu.cn)


Attentions
========

If you use Mysql as Event DB, pls set table character set to utf8mb4, 

because in java some chinese chracter use 4 bytes to encode and utf8 in mysql only use max to 3 bytes







