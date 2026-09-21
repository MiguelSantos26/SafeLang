# SafeLang Compiler

SafeLang é uma linguagem de programação compilada, desenvolvida no âmbito da UC de Compiladores (DETI, Universidade de Aveiro), cujo objetivo é ser o mais segura possível em tempo de compilação.

## Build

```bash
./build.sh
```

## Compile Safelang Files

```bash
./compile.sh <file path>
```

## Run

Run the last compiled file

```bash
./run.sh
```

Or a specific file

```bash
./run.sh <file path (.java)>
```

## Características Mínimas

Os exemplos min-*.sl, physics.sl e grades.sl indicam algum código fonte que
tem de ser aceite (e devidamente compilado) pela linguagem a desenvolver.
A linguagem deve implementar:

* Os tipo de de dados inteiro, real e texto (ver exemplo).
* Aceitar express˜oes aritméticas standard para os tipos de dados numéricos. Aceita a
operação de concatenação de texto (operador da soma).
* Instrução de escrita no standard output.
* Instrução de leitura de texto a partir do standard input.
* Operadores de conversão entre tipos de dados (por exemplo, string(10) para converter para texto; ou integer("10") para converter para inteiro).
* Operador de formatação de strings (número minimo de colunas e, eventualmente, justificação).
2
* Instrução para definir um novo tipo de dados (que serve também para definir uma nova
dimensão). Esta definição pode ser independente (isto é definir de raiz uma nova dimensão), ou dependente. Neste último caso a definição usa uma expressão aritmética
envolvendo apenas multiplicações, divisões e/ou potências envolvendo dimensões já definidas (ver exemplos). Em qualquer um destes casos, uma dimensão funciona como um
novo tipo de dados numérico (assente em inteiros ou em reais). A definição duma dimensão envolve também a definição da sua unidade base (por exemplo, a unidade metro
para a dimensão distância), e, eventualmente, um sufixo (por exemplo, o sufixo m para
a unidade metro).
* Instrução estática (isto é, apenas com significado em tempo de compilação) para incluir
o conteúdo de outro ficheiro (semanticamente similar ao include da linguagem C)
