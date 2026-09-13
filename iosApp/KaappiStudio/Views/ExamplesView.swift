import SwiftUI
import shared

struct ExamplesView: View {
    let onSelect: (String) -> Void

    /// The shared example list grouped in `ExampleCategory` declaration order,
    /// skipping empty categories.
    private var sections: [(ExampleCategory, [Example])] {
        ExampleCategory.entries.compactMap { category in
            let items = ExampleRepository.shared.examples.filter { $0.category == category }
            return items.isEmpty ? nil : (category, items)
        }
    }

    var body: some View {
        NavigationStack {
            List {
                ForEach(sections, id: \.0) { category, examples in
                    Section(header: Text(category.label)) {
                        ForEach(examples) { example in
                            Button {
                                onSelect(example.code)
                            } label: {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(example.title)
                                        .font(.subheadline.weight(.medium))
                                        .foregroundColor(.primary)
                                    Text(example.description_)
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Examples")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
