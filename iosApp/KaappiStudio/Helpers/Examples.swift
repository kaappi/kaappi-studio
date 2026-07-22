import Foundation

struct SchemeExample: Identifiable {
    let id: String
    let title: String
    let description: String
    let category: ExampleCategory
    let code: String
}

enum ExampleCategory: String, CaseIterable {
    case gettingStarted = "Getting Started"
    case functions = "Functions"
    case dataStructures = "Data Structures"
    case controlFlow = "Control Flow"
    case advanced = "Advanced"
}

let schemeExamples: [SchemeExample] = [
    SchemeExample(
        id: "hello", title: "Hello World",
        description: "Your first Scheme program",
        category: .gettingStarted,
        code: """
        (display "Hello from Kaappi!")
        (newline)
        """
    ),
    SchemeExample(
        id: "arithmetic", title: "Arithmetic",
        description: "Basic math operations",
        category: .gettingStarted,
        code: """
        (display "2 + 3 = ") (display (+ 2 3)) (newline)
        (display "10 - 4 = ") (display (- 10 4)) (newline)
        (display "6 * 7 = ") (display (* 6 7)) (newline)
        (display "22 / 7 = ") (display (/ 22.0 7)) (newline)
        (display "2^10 = ") (display (expt 2 10)) (newline)
        """
    ),
    SchemeExample(
        id: "variables", title: "Variables",
        description: "Defining and using variables",
        category: .gettingStarted,
        code: """
        (define name "Kaappi")
        (define version 21)

        (display "Welcome to ")
        (display name)
        (display " v0.")
        (display version)
        (display ".0!")
        (newline)
        """
    ),
    SchemeExample(
        id: "factorial", title: "Factorial",
        description: "Recursive and tail-recursive factorial",
        category: .functions,
        code: """
        (define (factorial n)
          (if (<= n 1)
              1
              (* n (factorial (- n 1)))))

        (define (factorial-tail n)
          (let loop ((i n) (acc 1))
            (if (<= i 1)
                acc
                (loop (- i 1) (* acc i)))))

        (display "10! = ")
        (display (factorial 10))
        (newline)

        (display "20! (tail) = ")
        (display (factorial-tail 20))
        (newline)
        """
    ),
    SchemeExample(
        id: "fibonacci", title: "Fibonacci",
        description: "Computing Fibonacci numbers",
        category: .functions,
        code: """
        (define (fib n)
          (if (<= n 1)
              n
              (+ (fib (- n 1))
                 (fib (- n 2)))))

        (display "fib(30) = ")
        (display (fib 30))
        (newline)
        """
    ),
    SchemeExample(
        id: "higher-order", title: "Higher-Order Functions",
        description: "Map, filter, and fold",
        category: .functions,
        code: """
        (import (srfi 1))

        (define nums '(1 2 3 4 5 6 7 8 9 10))

        (display "Squares of odds: ")
        (display
          (map (lambda (x) (* x x))
               (filter odd? nums)))
        (newline)

        (display "Sum: ")
        (display (fold + 0 nums))
        (newline)
        """
    ),
    SchemeExample(
        id: "lists", title: "Lists",
        description: "Creating and manipulating lists",
        category: .dataStructures,
        code: """
        (define fruits '("apple" "banana" "cherry" "date"))

        (display "Fruits: ") (display fruits) (newline)
        (display "First: ") (display (car fruits)) (newline)
        (display "Rest: ") (display (cdr fruits)) (newline)
        (display "Length: ") (display (length fruits)) (newline)
        (display "Reversed: ") (display (reverse fruits)) (newline)
        """
    ),
    SchemeExample(
        id: "conditionals", title: "Conditionals",
        description: "If, cond, and case expressions",
        category: .controlFlow,
        code: """
        (define (classify n)
          (cond
            ((negative? n) "negative")
            ((zero? n) "zero")
            ((even? n) "positive even")
            (else "positive odd")))

        (for-each
          (lambda (n)
            (display n) (display " is ")
            (display (classify n)) (newline))
          '(-3 0 4 7))
        """
    ),
    SchemeExample(
        id: "tail-recursion", title: "Tail Recursion",
        description: "Efficient loops via tail calls",
        category: .controlFlow,
        code: """
        (define (loop n acc)
          (if (= n 0)
              acc
              (loop (- n 1) (+ acc 1))))

        (display "1,000,000 iterations: ")
        (display (loop 1000000 0))
        (newline)
        """
    ),
    SchemeExample(
        id: "macros", title: "Macros",
        description: "Hygienic macros with syntax-rules",
        category: .advanced,
        code: """
        (define-syntax swap!
          (syntax-rules ()
            ((_ a b)
             (let ((tmp a))
               (set! a b)
               (set! b tmp)))))

        (define x 1)
        (define y 2)
        (display "Before: x=") (display x)
        (display " y=") (display y) (newline)
        (swap! x y)
        (display "After:  x=") (display x)
        (display " y=") (display y) (newline)
        """
    ),
    SchemeExample(
        id: "library", title: "Libraries",
        description: "Define and use R7RS libraries",
        category: .advanced,
        code: """
        (define-library (geometry shapes)
          (import (scheme base) (scheme write))
          (export circle-area rect-area describe)
          (begin
            (define pi 3.141592653589793)
            (define (circle-area r) (* pi r r))
            (define (rect-area w h) (* w h))
            (define (describe shape . args)
              (display shape) (display ": ")
              (display
                (cond
                  ((equal? shape "circle") (circle-area (car args)))
                  ((equal? shape "rect") (rect-area (car args) (cadr args)))
                  (else "unknown")))
              (newline))))

        (import (geometry shapes))
        (describe "circle" 5)
        (describe "rect" 3 4)
        """
    ),
]

func examplesByCategory() -> [(ExampleCategory, [SchemeExample])] {
    ExampleCategory.allCases.compactMap { cat in
        let items = schemeExamples.filter { $0.category == cat }
        return items.isEmpty ? nil : (cat, items)
    }
}
