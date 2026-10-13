//
//  BMICompassView.swift
//  FeatureOnboarding
//
//  Visual BMI gauge with WHO standard-banded arc.
//

import SwiftUI

/// A circular BMI gauge with WHO-standard color bands.
public struct BMICompassView: View {
    public let bmi: Double

    public init(bmi: Double) { self.bmi = bmi }

    public var body: some View {
        ZStack {
            Circle()
                .trim(from: 0, to: 1)
                .stroke(
                    AngularGradient(
                        gradient: Gradient(colors: [Color.blue, Color.green, Color.yellow, Color.orange, Color.red]),
                        center: .center
                    ),
                    style: StrokeStyle(lineWidth: 18, lineCap: .round)
                )
                .rotationEffect(.degrees(135))
                .frame(width: 210, height: 210)
            VStack(spacing: 4) {
                Text(String(format: "%.1f", bmi))
                    .font(.system(size: 56, weight: .bold, design: .rounded))
                    .monospacedDigit()
                Text("BMI (kg/m²)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}
