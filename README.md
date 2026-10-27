# Onion Architecture

Companion repository for Chapter 9 of [*Domain-Centric Architecture*](https://github.com/taeia/domain-centric-architecture): the library lending application with the inside of the hexagon arranged in the rings of Palermo's Onion Architecture, one Maven module per ring, every dependency pointing inward.

The rules, the aggregates, the domain services, the application services, the ports, and the records are those of Chapter 8, and so are the seventeen scenarios, the five adapters, and every sentence they print. What changed is the inside. Chapter 8's `core` module, thirty-five files in four packages with no rule between them, is now three modules, one per ring. `model` holds the domain model: the aggregates, the entities inside them, the value objects, and `LibraryException`. `domain` holds the domain services, `Lending` and `Reserving`, and the five interfaces they call, the four repositories and the `Clock`. `application` holds the four application services, the four driving ports they implement, the records that cross those ports, `Refusal`, and the two driven ports that only this ring calls, `Notifier` and `ChangeSignal`. The `ports` packages of Chapters 7 and 8 are gone: each port is declared in the innermost ring that calls it, for an outer ring to implement, which is Palermo's rule that inner layers define interfaces and outer layers implement them.

The poms are the rule. `model` lists nothing. `domain` lists `model`. `application` lists `domain` and `model`. On the outer ring, `inmemory` lists all three, because it stores aggregates, implements the repositories and the clock, and implements the notifier; `http` lists `application` alone, with `domain` and `inmemory` in test scope for its test's configurator; `desktop` lists `application`, `domain`, `inmemory`, and `http`; `cli` and `scenarios` list `application`, `domain`, `model`, and `inmemory`. A driver depends on `domain` because its configurator builds `Lending` and `Reserving` before it builds the services around them. An import that points outward does not compile: add `import library.domain.MemberRepository;` to `Member` and the compiler answers that `package library.domain does not exist`.

The library it implements, with its rules and scenarios, is described in [Chapter 0](https://github.com/taeia/domain-centric-architecture/blob/main/chapters/00-the-library.md). The chapter about this code is [Chapter 9](https://github.com/taeia/domain-centric-architecture/blob/main/chapters/09-onion-architecture.md).

## Running

You need Java 25 and Maven. JavaFX is a Maven dependency, so nothing else has to be installed.

```
mvn install
mvn -pl desktop javafx:run
```

The first command builds all eight modules and runs the tests. The second starts the window from the desktop module. Repeat the first command after changing anything outside `desktop`.

A window opens with the library in its starting state, and the status bar says that it also listens on `http://localhost:8080`. The date picker in the toolbar sets the library's idea of today, so you can walk through the scenarios of Chapter 0 and check the due dates and fines by hand. The *Reset library* button returns to the starting state.

Now open a terminal and borrow a copy from there:

```
curl -X POST "http://localhost:8080/borrow?member=John&copy=C1"
```

The answer is the sentence the window would have shown, and the Copies table in the window shows C1 on loan to John without a click. In PowerShell, write `curl.exe`, because `curl` alone is an alias for something else. The five commands are `POST /borrow?member=John&copy=C1`, `POST /return?copy=C1`, `POST /renew?copy=C1`, `POST /reserve?member=Mary&book=The%20Hobbit`, and `POST /pay?member=Jane`; the three listings are `GET /copies`, `GET /members`, and `GET /reservations`. A refusal answers with status 409 and the reason. There is no `day` over HTTP: the window owns the clock.

To start the window from an IDE, run `library.desktop.Launcher`. Running `MainWindow` directly fails with *JavaFX runtime components are missing*, because JavaFX is on the classpath and the Java launcher checks for the modules whenever the main class extends `Application`. `Launcher` does not extend it, so the check is skipped.

The same library on the command line:

```
mvn -q -pl cli exec:java
```

Type `help` for the commands. They are written the way Chapter 0 writes its scenarios: `day 2 March`, `borrow John C1`, `return C1`, `renew C1`, `reserve Mary The Hobbit`, `pay Jane`, and `copies`, `members`, `reservations` to look at the library. `quit` ends the session. A script runs the long scenario of Chapter 0 from start to finish:

```
mvn -q -pl cli exec:java -Dexec.args=cli/scripts/a-month-at-the-library.txt
```

## Tests

```
mvn test
```

`ScenarioTest` lives in the `scenarios` module and has one test per scenario of Chapter 0, with the same name. It holds the four driving ports, names members, copies, and books the way Chapter 0 does, reads the library back through the browsing port, and moves a `SettableClock` from day to day. It never sees an aggregate.

The unit tests of single rules split with the rings they test. The `model` module has the tests of the aggregates and the values, thirty-eight of them, which need no fake because the model asks nothing of anyone. The `domain` module has the eleven tests of `Lending` and `Reserving`, on fakes that implement four of the five interfaces the ring declares. The `cli` module tests the command line: what it prints for what it is told, including the whole month script. The `http` module tests the endpoint: what it answers for what it is sent, and that the change signal rings once per completed command and not for a refusal. Seventy-eight tests in all, the same seventy-eight as Chapter 8.

## Layout

```
pom.xml                        the parent: lists the eight modules, holds the versions
model/                         the centre: the domain model
  pom.xml                      depends on nothing (JUnit in test scope)
  src/main/java/library/model/
    Member.java                aggregate root: name, debt, loans; borrow, returnCopy, renew, pay
    Loan.java                  entity inside Member: due date, isOverdue, fine, each for a given day
    Queue.java                 aggregate root: the reservations of one book; join, leave, first
    Reservation.java           entity inside Queue
    Copy.java                  aggregate root: available, on loan, or on hold; lendTo, holdFor, makeAvailable
    Book.java                  aggregate root: ISBN and title
    Money.java                 value object: an amount in euros, never negative
    Fine.java                  value object: days of delay and what they cost
    LibraryException.java      thrown when the model refuses an operation
  src/test/java/library/model/
    MemberTest.java            unit tests of single rules, one class per domain class
    LoanTest.java
    QueueTest.java
    CopyTest.java
    MoneyTest.java
    FineTest.java
domain/                        the second ring: the domain services and the interfaces they call
  pom.xml                      depends on model
  src/main/java/library/domain/
    Lending.java               domain service: borrow, return, renew, across aggregates; asks the clock
    Reserving.java             domain service: reserve
    MemberRepository.java      all, named, save: declared here, implemented on the outer ring
    QueueRepository.java
    CopyRepository.java        all, ofBook, numbered, save
    BookRepository.java        all, titled, save
    Clock.java                 what day it is
  src/test/java/library/domain/
    LendingTest.java           the operations that cross aggregates, on the fakes below
    ReservingTest.java
    FakeMemberRepository.java  four of this ring's five interfaces, implemented for these tests
    FakeCopyRepository.java
    FakeQueueRepository.java
    FakeClock.java
application/                   the third ring: the application services, the driving ports, and what crosses them
  pom.xml                      depends on domain and model
  src/main/java/library/application/
    LoanService.java           implements ForLendingCopies: finds the aggregates by name, calls Lending, rings the signal
    ReservationService.java    implements ForReservingBooks: the same for Reserving
    MemberService.java         implements ForPayingFines: the same for Member.pay
    BrowsingService.java       implements ForBrowsingTheLibrary: reads the aggregates into summaries
    ForLendingCopies.java      driving port, commands: borrow, returnCopy, renew
    ForReservingBooks.java     driving port, command: reserve
    ForPayingFines.java        driving port, command: pay
    ForBrowsingTheLibrary.java driving port, queries: copies, members, books, reservations
    Borrowed.java              what the three lending commands answer with
    Returned.java
    Renewed.java
    CopySummary.java           what the queries answer with
    CopyStatus.java
    MemberSummary.java
    BookSummary.java
    ReservationSummary.java
    Refusal.java               thrown through a driving port when the library refuses
    Notifier.java              driven port, called only by LoanService: tell this member this
    ChangeSignal.java          driven port, called by the three command services: the library changed
inmemory/                      the outer ring: driven adapters
  pom.xml                      depends on model, domain, and application
  src/main/java/library/inmemory/
    InMemoryMemberRepository.java   the repository interfaces of the domain ring, implemented with lists
    InMemoryQueueRepository.java
    InMemoryCopyRepository.java
    InMemoryBookRepository.java
    SettableClock.java         the clock: set by the date picker, the day command, and the tests
    InMemoryNotifier.java      the notifier of the application ring: adds each message to a list the window shows
    StartingState.java         the starting state of Chapter 0
http/                          the outer ring: the HTTP endpoint
  pom.xml                      depends on application (domain and inmemory in test scope)
  src/main/java/library/http/
    HttpApi.java               one request becomes one call on a port; started by the desktop configurator
  src/test/java/library/http/
    HttpApiTest.java           what the endpoint answers for what it is sent
desktop/                       the outer ring: the JavaFX window
  pom.xml                      depends on application, domain, inmemory, and http
  src/main/java/library/desktop/
    MainWindow.java            start() is the configurator for the window and the endpoint; refresh() when the signal rings
    Launcher.java              starts MainWindow from an IDE
    Chooser.java               a combo box that shows a record by the text the dialog chooses
    Loans.java                 the copies on loan, described for the Return and Renew dialogs
    BorrowDialog.java          the five dialogs, each holding the ports it needs
    ReturnDialog.java
    RenewDialog.java
    ReserveDialog.java
    PayDialog.java
cli/                           the outer ring: the command line
  pom.xml                      depends on application, domain, model, and inmemory
  scripts/
    a-month-at-the-library.txt the long scenario of Chapter 0 as a script
  src/main/java/library/cli/
    Cli.java                   main() is the configurator; execute() turns a line into a call on a port
    ConsoleNotifier.java       a driven adapter owned by this driver: prints each message
  src/test/java/library/cli/
    CliTest.java               what the command line prints for what it is told
scenarios/                     the outer ring: the tests
  pom.xml                      depends on application, domain, model, and inmemory
  src/test/java/library/scenarios/
    ScenarioTest.java          one test per scenario
```
