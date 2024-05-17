# Filas com Redis e Spring

Este projeto traz exemplos com Java/Spring Boot de como 
usar filas do [Redis](https://redis.io/) para resolver problemas
de enfileiramento de tarefas.

A implementação usa o [Redisson](https://github.com/redisson/redisson) e as filas
[RQueue e RScoredSortedSet](https://github.com/redisson/redisson/wiki/7.-Distributed-collections)
para mostrar como podemos usar o redis junto de uma base de dados para resolver
problemas que precise de filas, especialmente o problema de enfileiramento de tarefas.

## Como usar este projeto

Clone o repositório e execute os testes contidos em [FilasRedisApplicationTests.java](src%2Ftest%2Fjava%2Fcom%2Fexample%2Fdemo%2FFilasRedisApplicationTests.java).

Os primeiros dois testes testProcessJobs e testProcessAsync são apenas para demonstração de como
podemos implementar filas diretamente no banco de dados, sem uso do redis. Essa prática tem alguns
problemas, especialmente no segunto teste, pois não foram implementadas quaisquer medidas de sincronização
entre threads que consomem do banco. No entanto, é possível implementar o comportamento desejado usando
apenas o banco de dados, com alguns tradeoffs de desempenho e algumas colunas a mais na entidade [Tarefa](src%2Fmain%2Fjava%2Fcom%2Fexample%2Fdemo%2Fmodel%2FTarefa.java).

Os dois últimos testes tentam mostrar como é possível implementar o comportamento de filas usando o Redis.
Primeiramente o teste testProcessAsyncRedis explora o uso da RQueue, uma fila simples que implementa
a política FIFO; por fim o teste testProcessAsyncRedisScoredSet mostra a utilização da estrutura RScoredSortedSet
que é um Set ordenado que pode ser usado como uma fila sem valores duplicados.