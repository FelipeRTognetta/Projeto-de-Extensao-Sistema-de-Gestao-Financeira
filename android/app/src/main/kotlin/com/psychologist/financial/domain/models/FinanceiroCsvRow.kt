package com.psychologist.financial.domain.models

/**
 * Represents one row of the monthly financial CSV export.
 *
 * One [FinanceiroCsvRow] is generated per payment. If a patient has two payments
 * in the selected month, two rows are produced.
 *
 * Payer columns (nomeResponsavel … enderecoResponsavel) are populated only when
 * the patient's [Patient.naoPagante] flag is true and a [PayerInfo] record exists;
 * otherwise they are empty strings (never null or omitted).
 *
 * @property nomePaciente Patient full name
 * @property cpfPaciente Patient CPF formatted (e.g. "123.456.789-09") or ""
 * @property emailPaciente Patient e-mail or ""
 * @property telefonePaciente Patient phone or ""
 * @property enderecoPaciente Patient address or ""
 * @property nomeResponsavel Payer full name or "" when patient pays directly
 * @property cpfResponsavel Payer CPF formatted or ""
 * @property emailResponsavel Payer e-mail or ""
 * @property telefoneResponsavel Payer phone or ""
 * @property enderecoResponsavel Payer address or ""
 * @property valorPagamento Payment amount as plain decimal string (e.g. "150.00")
 * @property dataPagamento Payment date formatted as "dd/MM/yyyy"
 */
data class FinanceiroCsvRow(
    val nomePaciente: String,
    val cpfPaciente: String,
    val emailPaciente: String,
    val telefonePaciente: String,
    val enderecoPaciente: String,
    val nomeResponsavel: String,
    val cpfResponsavel: String,
    val emailResponsavel: String,
    val telefoneResponsavel: String,
    val enderecoResponsavel: String,
    val valorPagamento: String,
    val dataPagamento: String
)
