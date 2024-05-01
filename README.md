# AliceDL
Alice in DistributedLand

## PROJECT PROPOSAL
The aim of the project is the simulation of a distributed system. 
The system is composed of an undefined number of servers connected to each other (is there a precise structure or are they just connected randomly?) -> PUT RANGE OF NUMBER OF SERVERS
Many client can be connected to the system (define max number of clients)
Many client can upload thier file (define max number of clients)
A client can request the file of JUST ITSELF/OTHERS
The file is divided in chunks and each one is sent to ONE/TWO/THREE/NUMBER BASED ON HOW MANY SERVERS specific server in the system. Since chunnks are not ordered, to each chunk is appended the number of the server which contains the following chunk of the file.
Each chunk is crypted using a key (SELECT ALGORITHM AND KEY DISTRIBUTION ALGORITHM).

Anonymity of client and server?
A can upload more than one file? -> give an ID to file
B can download A's file? -> whitelist in certificate
MA farlo direttamente peer to peer? 





AliceDL: Alice in DistributedLand

Enrico Carnelos [248722] - Annachiara Fortuna [248721]

Category: Protection of the data (hybrid)

The aim of the project is the creation of a distributed system to store secret file in a secure infrastructure. One or more users can upload and download the files in the cloud system, having the right key. 

This system response to the needed of storing important files. Saving the entire file in a unique server, means having a unique point of failure: in case the server does not respond due to internal error or for a DDos attack, the file is not available. Similarly, if the file is entirely duplicated in many servers, availability is guaranteed, but confidentiality can be compromise due to the increase of probability of having a vulnerable server. Instead, fragmentation takes best of both approaches.

The system is composed of an undefined number of servers connected to each other in a peer-to-peer relation and they are managed by a hash distributed table algorithm. New servers can join the system and others could leave. This will not compromise the entire system nether in terms of availability. 

When the user uploads a file, the latter will be divided in chunks and each chunk will be uploaded in a different server, based on the hash table algorithm. In addition, each chunk is uploaded in different servers; duplication is needed to maintain availability. Once the user download the file, a reconstruction system is provided to retrieve all the chunks from the right servers.





Requisiti:

To simulate the system will we use java sockets as local servers and JCA will be used for all the cryptographic primitives.

All the communication will be handled with the TLSv1.3 protocol and self signed certificate using the java SSLsocket library.

The chunks file are encrypted with a symmetric key algorithm and the keys are generate with a deterministic key scheduling algorithm so that with the master key all the derived key could be inferred. The cipher text of the chunks are stored in the server, so even retrieving a chunk will not disclose sensitive information.

The integrity of the entire file is checked during the download by the use of an hash digest


## LIMITATIONS:
* The derivation function of subkeys is secure for the derivation of a certain number of keys from the same masterkey. If this term is exceeded, security is not provided. 
"subkey_id can be any value up to (2^64)-1. However, with 128-bit subkeys, it is not safe to derive more than 2^48 subkeys from the same key. With subkeys that are at 160 bits or more, 2^64 subkeys can be safely derived." 
[https://libsodium.gitbook.io/doc/key_derivation]
* If the user uses a weak password, what happens?


#### Link to report: [https://docs.google.com/document/d/1rytibSqQ1kqMa_7QtuciJx6qO_HyYezP1Xiwrp24CSA/edit?usp=sharing]





